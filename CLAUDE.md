# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SafePalAgent is an Android movie and TV show recommendation application built with Kotlin and Jetpack Compose. The app uses TMDB API for content data and integrates with Koog AI agents to provide personalized recommendations based on user's streaming platforms.

**Key Details:**
- **Package**: `com.safepal.agent`
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Compile SDK**: 36
- **Kotlin Version**: 2.0.21
- **AGP Version**: 8.11.1
- **Architecture**: MVVM with Koog AI agents
- **APIs**: TMDB v3, OpenAI (via Koog)

## Development Commands

### Building the Project
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease

# Clean build
./gradlew clean

# Build and install debug APK to connected device
./gradlew installDebug
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests com.safepal.agent.ExampleUnitTest

# Run all tests
./gradlew check
```

### Code Quality
```bash
# Lint check
./gradlew lint

# Generate lint report
./gradlew lintDebug
```

## Project Architecture

### Module Structure
- **Root module**: Main project configuration
- **app module**: Contains all application code, resources, and tests

### Code Organization
```
app/src/main/java/com/safepal/agent/
├── MainActivity.kt                         # Main entry point with navigation
├── MovieRecommendationApplication.kt      # Application class with Koin setup
├── api/
│   └── TMDBClient.kt                      # TMDB API client and data models
├── agents/
│   ├── common/
│   │   ├── AgentProvider.kt               # Agent interface
│   │   └── ExitTool.kt                   # Common exit tool
│   └── movie/
│       ├── MovieAgentProvider.kt          # Movie recommendation agent
│       └── MovieTools.kt                 # TMDB API integration tools
├── di/
│   └── AppModule.kt                      # Koin dependency injection modules
├── settings/
│   └── AppSettings.kt                    # DataStore preferences management
├── ui/
│   ├── MovieRecommendationViewModel.kt   # MVVM pattern implementation
│   ├── components/                       # Individual composable components
│   │   ├── ChatInputSection.kt          # Message input UI
│   │   ├── MessageItem.kt               # Individual message display
│   │   ├── PlatformSelectionCard.kt     # Platform selection UI
│   │   ├── RecommendationItem.kt        # Individual recommendation card
│   │   └── RecommendationsCard.kt       # Recommendations container
│   ├── screens/                         # Screen-level composables
│   │   ├── MovieRecommendationScreen.kt # Main chat interface
│   │   └── SettingsScreen.kt           # OpenAI API key configuration
│   └── theme/                           # Material Design 3 theming
└── ...
```

### Key Technologies
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Koog AI agents, Single Activity with Compose navigation
- **Dependency Injection**: Koin for clean dependency management
- **AI Integration**: Koog agents framework for conversational AI
- **HTTP Client**: Ktor for TMDB API requests
- **Data Storage**: DataStore for preferences (OpenAI API key)
- **Image Loading**: Coil for movie posters
- **Testing**: JUnit 4 for unit tests, Espresso for instrumented tests
- **Build System**: Gradle with Kotlin DSL and version catalogs

### Dependencies Management
Dependencies are managed through `gradle/libs.versions.toml` using Gradle's version catalog feature. Major dependencies include:
- AndroidX Core KTX, Compose BOM, Material Design 3
- Koin for dependency injection (koin-core, koin-android, koin-androidx-compose)
- Koog agents framework (ai.koog:koog-agents)
- Ktor client with JSON serialization
- Coil for image loading
- DataStore for preferences
- Coroutines for async operations

### Testing Strategy
- **Unit Tests**: Located in `app/src/test/` for business logic testing
- **Instrumented Tests**: Located in `app/src/androidTest/` for UI and integration testing
- Test runner: AndroidJUnitRunner for instrumented tests

## Development Environment Setup
1. Ensure Android SDK is installed with API level 36
2. Use JDK 17 for compilation (required for Koog framework)
3. Sync project with Gradle files
4. Configure API keys:
   - OpenAI API key (required): Configure in Settings screen within the app
   - TMDB API key (hardcoded): ce2eb742633db1119130842dff34c3eb
5. For device testing, enable USB debugging on Android device or use emulator

## App Features
- **Chat Interface**: Conversational UI for movie/TV recommendations
- **Platform Selection**: Choose from 5 popular streaming platforms (Netflix, Prime Video, Disney+, HBO Max, Hulu)
- **Smart Recommendations**: AI-powered suggestions based on selected platforms
- **Content Search**: Search for specific movies or TV shows
- **Visual Content Cards**: Movie posters, ratings, and descriptions
- **Settings**: Configure OpenAI API key for AI functionality

## API Integration
- **TMDB API**: Used for movie/TV data, streaming platform information, and content discovery
- **OpenAI API**: Powers the conversational AI agent via Koog framework
- **Koog Framework**: Handles agent workflows, tool execution, and conversation management

## Common Issues & Solutions

### DataStore Multiple Instances Error
**Problem**: "There are multiple DataStores active for the same file" error when using AppSettings.
**Solution**: DataStore is created as a top-level extension property to ensure singleton behavior:
```kotlin
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
```

### Input Field Behind Navigation Bar
**Problem**: Chat input field appears behind the device's navigation bar.
**Solution**: Applied proper WindowInsets handling:
- `contentWindowInsets = WindowInsets(0)` in Scaffold
- `.navigationBarsPadding()` modifier in ChatInputSection
- Edge-to-edge display with proper padding management