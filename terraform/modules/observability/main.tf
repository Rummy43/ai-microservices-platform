locals {
  prefix           = "${var.project}-${var.environment}"
  tempo_bucket     = "${local.prefix}-tempo-traces"
  loki_bucket      = "${local.prefix}-loki-chunks"
  oidc_host        = replace(var.oidc_provider_url, "https://", "")
}

# ──────────────────────────────────────────────
# S3 — Tempo trace storage
# ──────────────────────────────────────────────

resource "aws_s3_bucket" "tempo" {
  bucket        = local.tempo_bucket
  force_destroy = true  # evidence-capture workflow: destroy wipes all traces

  tags = { Name = local.tempo_bucket, Component = "observability" }
}

resource "aws_s3_bucket_versioning" "tempo" {
  bucket = aws_s3_bucket.tempo.id
  versioning_configuration { status = "Disabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tempo" {
  bucket = aws_s3_bucket.tempo.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_public_access_block" "tempo" {
  bucket                  = aws_s3_bucket.tempo.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ──────────────────────────────────────────────
# S3 — Loki log chunk storage
# ──────────────────────────────────────────────

resource "aws_s3_bucket" "loki" {
  bucket        = local.loki_bucket
  force_destroy = true

  tags = { Name = local.loki_bucket, Component = "observability" }
}

resource "aws_s3_bucket_versioning" "loki" {
  bucket = aws_s3_bucket.loki.id
  versioning_configuration { status = "Disabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "loki" {
  bucket = aws_s3_bucket.loki.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_public_access_block" "loki" {
  bucket                  = aws_s3_bucket.loki.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ──────────────────────────────────────────────
# IRSA — Tempo pod IAM role
# ──────────────────────────────────────────────

data "aws_iam_policy_document" "tempo_s3" {
  statement {
    sid    = "TempoS3ReadWrite"
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject",
      "s3:ListBucket",
      "s3:GetBucketLocation",
    ]
    resources = [
      aws_s3_bucket.tempo.arn,
      "${aws_s3_bucket.tempo.arn}/*",
    ]
  }
}

resource "aws_iam_policy" "tempo_s3" {
  name   = "${local.prefix}-tempo-s3-policy"
  policy = data.aws_iam_policy_document.tempo_s3.json
}

data "aws_iam_policy_document" "tempo_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:sub"
      values   = ["system:serviceaccount:${var.k8s_namespace}:tempo"]
    }
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "tempo" {
  name               = "${local.prefix}-tempo-irsa"
  assume_role_policy = data.aws_iam_policy_document.tempo_assume.json
}

resource "aws_iam_role_policy_attachment" "tempo_s3" {
  role       = aws_iam_role.tempo.name
  policy_arn = aws_iam_policy.tempo_s3.arn
}

# ──────────────────────────────────────────────
# IRSA — Loki pod IAM role
# ──────────────────────────────────────────────

data "aws_iam_policy_document" "loki_s3" {
  statement {
    sid    = "LokiS3ReadWrite"
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject",
      "s3:ListBucket",
      "s3:GetBucketLocation",
    ]
    resources = [
      aws_s3_bucket.loki.arn,
      "${aws_s3_bucket.loki.arn}/*",
    ]
  }
}

resource "aws_iam_policy" "loki_s3" {
  name   = "${local.prefix}-loki-s3-policy"
  policy = data.aws_iam_policy_document.loki_s3.json
}

data "aws_iam_policy_document" "loki_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:sub"
      values   = ["system:serviceaccount:${var.k8s_namespace}:loki"]
    }
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "loki" {
  name               = "${local.prefix}-loki-irsa"
  assume_role_policy = data.aws_iam_policy_document.loki_assume.json
}

resource "aws_iam_role_policy_attachment" "loki_s3" {
  role       = aws_iam_role.loki.name
  policy_arn = aws_iam_policy.loki_s3.arn
}

# ──────────────────────────────────────────────
# SES — sender identity verification
# ──────────────────────────────────────────────

resource "aws_ses_email_identity" "alert_sender" {
  email = var.alert_email
}

# ──────────────────────────────────────────────
# SES SMTP — IAM user for Alertmanager
# An IAM user (not a role) is required because Alertmanager SMTP
# needs static long-lived credentials, not temporary STS tokens.
# ──────────────────────────────────────────────

resource "aws_iam_user" "ses_smtp" {
  name = "${local.prefix}-alertmanager-ses-smtp"
  tags = { Component = "observability" }
}

data "aws_iam_policy_document" "ses_send" {
  statement {
    sid     = "SESSmtpSend"
    effect  = "Allow"
    actions = ["ses:SendRawEmail"]
    resources = ["*"]
  }
}

resource "aws_iam_user_policy" "ses_send" {
  name   = "${local.prefix}-ses-send"
  user   = aws_iam_user.ses_smtp.name
  policy = data.aws_iam_policy_document.ses_send.json
}

resource "aws_iam_access_key" "ses_smtp" {
  user = aws_iam_user.ses_smtp.name
}
