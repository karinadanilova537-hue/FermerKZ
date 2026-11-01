# Фермер KZ — стартовый Android-проект

## Что уже заложено
- Android-приложение на Kotlin.
- Google Maps SDK for Android.
- Спутниковый режим и переключение на обычную карту.
- Добавление вершин участка нажатием на карту.
- Расчёт примерной площади по сферической формуле и периметра.
- GPS-точка по последнему известному местоположению телефона.
- Локальное сохранение измерений и объявлений.
- Демонстрационные разделы продажи и аренды земли.
- GitHub Actions workflow для сборки APK в облаке.

## Важно
Это стартовый исходный проект, а не готовый APK. Для сборки нужен Android build runner (например, GitHub Actions) и настоящий API-ключ Maps SDK for Android. Тестовый Google Maps JavaScript Demo Key не подходит для Android SDK.

## Подготовка Google Maps
1. Открой Google Cloud Console и выбери проект.
2. Включи **Maps SDK for Android**.
3. Создай отдельный API key для Android.
4. Включи Billing для проекта Google Maps Platform, если Google требует это для твоего проекта.
5. Ограничь ключ: API restriction — только Maps SDK for Android. После получения SHA-1 сертификата debug/release добавь ограничение Android application (package `kz.fermerkz.app` + SHA-1).
6. Не отправляй ключ в чат и не публикуй его в открытом репозитории.

Официальная инструкция:
https://developers.google.com/maps/documentation/android-sdk/get-api-key

## Сборка APK через GitHub Actions без Android Studio на телефоне
1. Создай репозиторий GitHub и загрузи в него содержимое этой папки.
2. В GitHub открой Settings → Secrets and variables → Actions → New repository secret.
3. Имя секрета: `MAPS_API_KEY`. Значение — Android Maps API key.
4. Открой вкладку Actions и запусти workflow **Build Android APK**.
5. После успешной сборки открой run workflow → Artifacts → скачай `FermerKZ-debug-apk`.
6. Установи APK на Samsung. Android может попросить разрешить установку из браузера/файлового менеджера.

Workflow использует GitHub-hosted runner и Gradle. Сборка требует интернет и может занять несколько минут.

## Ограничения этой первой версии
- Объявления и измерения сохраняются локально на одном устройстве. Общая база, аккаунты, SMS-вход, модерация, фотографии и чат ещё не подключены.
- GPS добавляет последнюю известную точку. Для точного полевого обхода понадобится непрерывное GPS-отслеживание и проверка качества сигнала.
- Площадь является ориентировочной. Для кадастровых/юридических работ нужна геодезическая проверка.
- API-ключ должен быть ограничен по приложению и API; не храните его в исходном коде.

## Идентификатор приложения
`kz.fermerkz.app`
