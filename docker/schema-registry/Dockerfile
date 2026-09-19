FROM confluentinc/cp-schema-registry:7.5.0

# Bake MSK IAM auth JAR directly into the schema-registry classpath directory.
# Confluent's kafka-run-class hardcodes /usr/share/java/schema-registry/* and does
# NOT honour the CLASSPATH env var — adding the JAR here is the only reliable path.
# Docker ADD creates files owned by root:root with mode 600 — chmod to world-readable
# so the container's appuser (uid 1000) can load it from the JVM classpath.
USER root
ADD https://repo1.maven.org/maven2/software/amazon/msk/aws-msk-iam-auth/2.3.2/aws-msk-iam-auth-2.3.2-all.jar \
    /usr/share/java/schema-registry/aws-msk-iam-auth.jar
RUN chmod 644 /usr/share/java/schema-registry/aws-msk-iam-auth.jar
USER appuser
