# Publishing to Maven Central

This document describes how to publish grpc-kt to Maven Central using the new Central Portal.

## Prerequisites

1. **Sonatype Central Account**: Create account at https://central.sonatype.com
2. **GPG Key**: Generate and configure GPG key for signing
3. **Namespace**: Verify ownership of `io.github.imonja` namespace

## Setup

### 1. GPG Key Generation

```bash
# Generate GPG key
gpg --full-generate-key

# List keys
gpg --list-secret-keys --keyid-format LONG

# Export private key for signing
gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc

# Convert to base64 for environment variable
base64 private-key.asc > private-key-base64.txt
```

### 2. Environment Variables

Set these in your environment or `~/.gradle/gradle.properties`:

```properties
# Maven Central credentials
mavenCentralUsername=your-sonatype-username
mavenCentralPassword=your-sonatype-password

# GPG signing (in-memory method)
signingKey=your-base64-encoded-private-key
signingPassword=your-gpg-password
```

### 3. Gradle Properties

Update `gradle.properties` with your credentials:

```properties
# For local development
mavenCentralUsername=your-username
mavenCentralPassword=your-password
signingKey=your-base64-key
signingPassword=your-gpg-password
```

## Publishing Process

### 1. Update Version

Edit `gradle.properties`:
```properties
version=1.1.0  # Remove -SNAPSHOT for release
```

### 2. Build and Publish

```bash
# Clean build
./gradlew clean

# Publish to Maven Central
./gradlew publishToMavenCentral

# Or publish all variants
./gradlew publishAllPublicationsToMavenCentralRepository
```

### 3. Verify Publication

1. Check https://central.sonatype.com for your publication
2. Verify artifacts are signed and include:
   - JAR file
   - Sources JAR (`-sources.jar`)
   - Javadoc JAR (`-javadoc.jar`)
   - POM file
   - GPG signatures (`.asc` files)

### 4. Release Process

1. Publications automatically sync to Maven Central
2. Usually takes 10-30 minutes to appear in Maven Central
3. Update version back to `X.Y.Z-SNAPSHOT` for development

## GitHub Actions (Optional)

Create `.github/workflows/publish.yml`:

```yaml
name: Publish to Maven Central

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2
        
      - name: Publish to Maven Central
        run: ./gradlew publishToMavenCentral
        env:
          MAVEN_CENTRAL_USERNAME: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          MAVEN_CENTRAL_PASSWORD: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
```

## Available Gradle Tasks

- `publishToMavenCentral` - Publish to Maven Central
- `publishAllPublicationsToMavenCentralRepository` - Publish all modules
- `publishToMavenLocal` - Publish to local repository for testing

## Troubleshooting

### Common Issues

1. **GPG Signing Errors**: Ensure GPG key is properly configured
2. **Authentication Failures**: Verify Sonatype credentials
3. **Missing Artifacts**: Check that sources and javadoc JARs are generated
4. **POM Validation**: Ensure all required POM metadata is present

### Plugin Documentation

- [Vanniktech Maven Publish Plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)
- [Sonatype Central Portal](https://central.sonatype.org/publish/publish-portal-gradle/)

## Migration from OSSRH

This project uses the new Central Portal instead of the legacy OSSRH. Key differences:

1. **Simplified Process**: No staging repository management
2. **Automatic Sync**: Publications automatically sync to Maven Central
3. **New URLs**: Uses `https://central.sonatype.com` instead of `s01.oss.sonatype.org`
4. **Community Plugins**: Official Gradle support is on the roadmap