#!/bin/bash
# Build BT Audio Controller APK on any Linux x86-64 machine
# Usage: ./build-apk.sh

set -e

echo "========================================="
echo "  BT Audio Controller - APK Builder"
echo "========================================="

# Check for Java
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Install JDK 17+ first."
    echo "   Ubuntu/Debian: sudo apt install openjdk-17-jdk"
    echo "   Fedora: sudo dnf install java-17-openjdk"
    exit 1
fi

echo "✓ Java: $(java -version 2>&1 | head -1)"

# Download Gradle if not present
if [ ! -f gradlew ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p gradle/wrapper
    curl -sL "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar" \
        -o gradle/wrapper/gradle-wrapper.jar
    
    cat > gradlew << 'GRADLEW'
#!/bin/sh
exec java -Xmx64m -Xms64m \
  -Dorg.gradle.appname=gradlew \
  -classpath "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
GRADLEW
    chmod +x gradlew
fi

# Build the APK
echo ""
echo "🔨 Building APK..."
./gradlew assembleDebug --no-daemon

echo ""
echo "========================================="
echo "✅ Build complete!"
echo "📦 APK location:"
ls -lh app/build/outputs/apk/debug/*.apk
echo "========================================="
echo ""
echo "Install on your phone:"
echo "  adb install app/build/outputs/apk/debug/*.apk"
echo ""
echo "Or copy to phone and tap to install."
