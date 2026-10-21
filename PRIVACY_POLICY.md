# Privacy Policy

**Wallhavend** is an Android app that automatically sets wallpapers from [Wallhaven](https://wallhaven.cc), [Openverse](https://openverse.org), and [Pexafy](https://pexafy.com). This policy explains what data the app uses and how.

## Data collected and stored

All data is stored **locally on your device only** and is never transmitted to any server operated by this app.

| Data                                                               | Purpose                                                     | Where stored               |
|--------------------------------------------------------------------|-------------------------------------------------------------|----------------------------|
| Wallhaven API key (optional)                                       | Authenticate requests to the Wallhaven API for NSFW content | Device storage (DataStore) |
| Pexafy API key (optional)                                          | Authenticate requests to the Pexafy API when enabled        | Device storage (DataStore) |
| Search preferences (query, categories, purity, aspect ratio, etc.) | Remember your wallpaper settings                            | Device storage (DataStore) |
| Downloaded wallpaper images                                        | Display as device wallpaper                                 | Device internal storage    |

## Third-party services

The app communicates with the enabled wallpaper sources (**[Wallhaven](https://wallhaven.cc)**, **[Openverse](https://openverse.org)**, and **[Pexafy](https://pexafy.com)**) to fetch wallpaper images based on your configured search settings. If you provide a Wallhaven or Pexafy API key, it is included in requests to the corresponding service. Each service's own privacy policy applies to those requests.

No other third-party analytics, advertising, or tracking services are used.

## Permissions

| Permission                                              | Reason                                             |
|---------------------------------------------------------|----------------------------------------------------|
| `INTERNET`                                              | Fetch wallpapers from enabled sources              |
| `ACCESS_NETWORK_STATE`                                  | Respect the "unmetered connections only" setting   |
| `SET_WALLPAPER`                                         | Apply the downloaded image as wallpaper            |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Run the wallpaper update service in the background |
| `RECEIVE_BOOT_COMPLETED`                                | Restart the service automatically on device boot   |
| `POST_NOTIFICATIONS`                                    | Show service status notifications                  |

## Contact

For questions or concerns, open an issue at [https://github.com/Attacktive/Wallhavend-android/issues](https://github.com/Attacktive/Wallhavend-android/issues).
