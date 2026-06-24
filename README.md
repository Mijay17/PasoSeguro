# PasoSeguro — Prototipo Jetpack Compose v2

Aplicación Android de navegación asistida con IA para personas con discapacidad visual.

## Requisitos

| Herramienta | Versión mínima |
|-------------|---------------|
| Android Studio | Hedgehog (2023.1.1) o superior |
| Kotlin | 2.0.0 |
| Compose BOM | 2024.08.00 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 |

## Cómo abrir el proyecto

1. Descomprime `PasoSeguro.zip`.
2. En Android Studio → **File → Open** → selecciona la carpeta `PasoSeguro`.
3. Espera que Gradle sincronice (la primera vez descarga ~200 MB).
4. Ejecuta en un emulador API 26+ o dispositivo físico.

---

## Estructura del proyecto

```
app/src/main/java/com/pasoseguro/app/
│
├── MainActivity.kt                        Entry point, edge-to-edge
│
├── navigation/
│   ├── Feature.kt       ← Enum con los 6 features (ícono, colores, TTS, ruta)
│   ├── Screen.kt        ← Rutas selladas de navegación
│   └── NavGraph.kt      ← NavHost con animaciones slide + fade
│
├── screens/
│   ├── HomeScreen.kt    ← Pantalla principal (barras + carrusel)
│   └── FeatureScreen.kt ← Placeholder navegable con TTS al entrar
│
├── components/
│   ├── FeatureCarousel.kt      ← HorizontalPager 7 páginas + dots
│   ├── CarouselWelcomePage.kt  ← Slide 0: logo, bienvenida, hint
│   ├── CarouselFeaturePage.kt  ← Slides 1-6: ícono grande + descripción + botón
│   ├── PerimeterButton.kt      ← Botón circular accesible (64 dp touch target)
│   └── PasoSeguroLogo.kt       ← Logo dibujado en Canvas (reemplazable)
│
└── ui/theme/
    ├── Color.kt   ← Paleta accesible (alto contraste WCAG AA)
    └── Theme.kt   ← Material3 + tipografía escalada para baja visión

app/src/main/res/
├── drawable/
│   ├── ic_launcher.xml        ← Ícono cuadrado (vector)
│   └── ic_launcher_round.xml  ← Ícono circular (vector)
└── values/
    ├── strings.xml   ← Todos los textos + labels de accesibilidad
    └── themes.xml
```

---

## Características implementadas

### Pantalla principal
- **Barra superior**: Contactos · Notificaciones · Configuración
- **Carrusel central 7 slides**:
  - Slide 1: Logo + nombre + bienvenida + hint de deslizar
  - Slides 2–7: Ícono grande + título + descripción + botón "Abrir"
- **Barra inferior**: Navegar · Escanear · Ruta

### Retroalimentación sensorial
| Acción | Respuesta |
|--------|-----------|
| Tap en botón | Vibración corta (48 ms) |
| Tap en botón | TTS anuncia el nombre de la función |
| Entrar a pantalla | TTS anuncia el nombre y "próximamente" |

### Animaciones
- **Transición entre pantallas**: slide horizontal + fade (300 ms)
- **Botones perimetrales**: spring scale al presionar
- **Dots del carrusel**: ancho animado con spring

### Accesibilidad
- Touch targets mínimos: 64 dp (supera el mínimo de 48 dp)
- `contentDescription` en todos los botones e íconos
- Tipografía: mínimo 12 sp en labels, 15 sp en body
- Colores de alto contraste (ratio WCAG AA)
- Compatible con TalkBack

---

## Agregar el logo real

1. Copia el archivo PNG a `app/src/main/res/drawable/ic_logo_pasoseguro.png`
2. En `CarouselWelcomePage.kt`, reemplaza:
   ```kotlin
   PasoSeguroLogo(size = 110.dp, ...)
   ```
   por:
   ```kotlin
   Image(
       painter = painterResource(R.drawable.ic_logo_pasoseguro),
       contentDescription = stringResource(R.string.cd_logo),
       modifier = Modifier.size(110.dp)
   )
   ```

---

## Próximas fases

| Fase | Funcionalidad |
|------|---------------|
| 2 | CameraX + detección de obstáculos (ML Kit / TFLite) |
| 3 | GPS + navegación guiada paso a paso |
| 4 | Contactos de emergencia + alertas SMS |
| 5 | Room DB para destinos guardados |
| 6 | Backend + análisis de trayectos |
