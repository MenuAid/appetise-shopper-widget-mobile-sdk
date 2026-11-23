# Appetise Shopper mobile SDKs

The Appetise shopper widget in your iOS and Android apps. Pass a shopping list or a recipe, and
your users price it at their supermarket and send it to their cart, in a native sheet over your
app.

| Platform | Package | Requires |
| --- | --- | --- |
| iOS | Swift package `AppetiseShopper` | iOS 15+ |
| Android | `io.appetise:shopper` on Maven Central | API 24+ |

React Native and Flutter wrappers are planned.

> **Pre-release.** The first version will be `0.1.0`. Until then the install lines below don't
> resolve.

## Before you start

You need an Appetise partner ID. Appetise also has to add your app to your partner account: give
us your iOS bundle ID and Android package name, exactly as they appear in your build settings. The
widget refuses to load in any app that isn't on that list.

## Install

**iOS.** In Xcode, choose File › Add Package Dependencies, enter
`https://github.com/MenuAid/appetise-shopper-widget-mobile-sdk` and pick the `AppetiseShopper`
library. Or, in `Package.swift`:

```swift
.package(url: "https://github.com/MenuAid/appetise-shopper-widget-mobile-sdk", from: "0.1.0")
```

**Android.** With `mavenCentral()` in your repositories:

```kotlin
implementation("io.appetise:shopper:0.1.0")
```

## Use

### 1. Configure once, at launch

```swift
Appetise.configure(AppetiseConfiguration(partner: "your-partner-id"))
```

```kotlin
Appetise.configure(applicationContext, AppetiseConfiguration(partner = "your-partner-id"))
```

The country is worked out from the device's timezone. Pass `countryCode` (`"NZ"` or `"AU"`) to
set it yourself.

### 2. Preload, and show your button only when the widget is ready

Call `preload()` when a screen with a Shop button appears. Keep the button hidden until the widget
says it's ready, so it never does nothing when tapped.

```swift
Appetise.preload()
readiness = Appetise.onReadinessChange { ready in shopButton.isHidden = !ready }
```

```kotlin
Appetise.preload()
readiness = Appetise.onReadinessChange { ready -> shopButton.isVisible = ready }
```

Keep the subscription for as long as the screen is showing. On iOS it cancels itself when
released; on Android, call `readiness.cancel()` when the screen is destroyed. `isReady` and
`notReadyReason` give the current state at any time.

### 3. Open a shopping list or a recipe

```swift
Appetise.open([.raw("100g butter"), .named("eggs", quantity: 3)], externalRef: "list-42")

Appetise.openRecipe(.params(
    name: "Creamy mushroom pasta",
    ingredients: [.raw("250g pasta"), .raw("300g mushrooms")],
    externalRef: "recipe-123",
    servings: 4
))
```

```kotlin
Appetise.open(activity, listOf(AppetiseIngredient.Raw("100g butter")), externalRef = "list-42")

Appetise.openRecipe(activity, AppetiseRecipe.Params(
    name = "Creamy mushroom pasta",
    ingredients = listOf(AppetiseIngredient.Raw("250g pasta"), AppetiseIngredient.Raw("300g mushrooms")),
    externalRef = "recipe-123",
    servings = 4,
))
```

`externalRef` is your own ID for the list or recipe, so the baskets it leads to can be reported
back against it. A recipe can also be plain text: `.text(...)` on iOS, `AppetiseRecipe.Text` on
Android.

The sheet closes itself when the user is done. `Appetise.close()` closes it from your code.

### Events

`Appetise.on("embed_opened") { payload in ... }` listens for the widget's events, such as
`embed_initialised` and `embed_opened`. It returns a subscription, like `onReadinessChange`.

### Debugging

`AppetiseConfiguration(partner: ..., debug: true)` logs the SDK's lifecycle to the console
(Logcat on Android) and makes the WebView inspectable from Safari or Chrome.

## Privacy

The iOS package ships a privacy manifest (`PrivacyInfo.xcprivacy`). A full list of the data the
SDK collects, in App Store privacy label and Play Data safety terms, will be published before
1.0.

## License

Apache 2.0. See [LICENSE](LICENSE).
