FROM ghcr.io/cirruslabs/android-sdk:35

ENV ANDROID_HOME=/opt/android-sdk-linux
ENV ANDROID_SDK_ROOT=/opt/android-sdk-linux
ENV GRADLE_VERSION=8.10.2
ENV PATH=/opt/gradle/bin:${ANDROID_HOME}/platform-tools:${PATH}
ARG ANDROID_BUILD_TOOLS_VERSION

RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s "/opt/gradle-${GRADLE_VERSION}" /opt/gradle \
    && rm /tmp/gradle.zip

# Compose supplies this arg by default. Raw docker builds can omit it when they
# only need the base runner image and not a preinstalled Build Tools package.
RUN if [ -n "${ANDROID_BUILD_TOOLS_VERSION}" ]; then \
      printf '%s\n' "${ANDROID_BUILD_TOOLS_VERSION}" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
        || { echo "Invalid ANDROID_BUILD_TOOLS_VERSION: ${ANDROID_BUILD_TOOLS_VERSION}"; exit 1; }; \
      sdkmanager "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"; \
    fi

WORKDIR /workspace
CMD ["bash"]
