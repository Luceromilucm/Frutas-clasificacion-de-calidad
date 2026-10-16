# 🍎 FrutaScan AI — Clasificador Inteligente de Frutas en Tiempo Real

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![ONNX Runtime](https://img.shields.io/badge/ONNX_Runtime-1.20.0-005CED?style=for-the-badge&logo=onnx&logoColor=white)](https://onnxruntime.ai/)
[![CameraX](https://img.shields.io/badge/CameraX-1.4.1-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://developer.android.com/training/camerax)
[![API](https://img.shields.io/badge/Min_SDK-25_|_Target_SDK-35-orange?style=for-the-badge)](https://developer.android.com/)

**FrutaScan AI** es una aplicación móvil nativa para Android diseñada para clasificar frutas e identificar su estado de frescura (**Fresca** vs. **Podrida**) en tiempo real mediante la cámara del dispositivo. 

Utiliza una red neuronal **EfficientNet-B0** exportada a **ONNX Runtime Mobile**, logrando inferencias de baja latencia (~30–45 ms) en el propio dispositivo (*on-device AI*) sin requerir conexión a internet ni enviar datos a la nube.

---

## ✨ Características Principales

- ⚡ **Inferencia en Tiempo Real**: Análisis continuo de fotogramas a través del pipeline `ImageAnalysis` de CameraX con estrategia `STRATEGY_KEEP_ONLY_LATEST`.
- 🧠 **Motor ONNX Runtime**: Ejecución ultrarrápida del modelo `effnet_b0_tl.onnx` optimizado para dispositivos móviles.
- 📐 **Preprocesamiento Idéntico al Entrenamiento (Letterbox)**:
  - Redimensionamiento proporcional a `224x224` preservando la relación de aspecto sin deformaciones.
  - Relleno de bordes con la **mediana del color de los extremos**.
  - Normalización estándar de ImageNet (`mean = [0.485, 0.456, 0.406]`, `std = [0.229, 0.224, 0.225]`).
- 🎨 **Interfaz de Usuario Moderna & UX Pulida**:
  - **Retícula HUD de escaneo**: Guía visual central con borde punteado para centrar el objetivo.
  - **Semáforo dinámico de frescura**: Insignias en verde esmeralda para frutas en buen estado y rojo coral para frutas en descomposición.
  - **Desglose Top-3**: Barras horizontales de probabilidad que reflejan las 3 predicciones con mayor certeza.
  - **Control de Linterna (Flash)**: Acceso rápido para escanear en entornos con baja iluminación.
  - **Indicador de latencia**: Medidor en vivo del tiempo exacto de inferencia en milisegundos (`⚡ ms`).

---

## 🏷️ Clases Clasificadas

El modelo distingue entre 8 clases en total con traducción directa al español:

| Clase Original | Fruta | Condición |
|---|---|---|
| `FreshApple` | 🍏 Manzana | Fresca |
| `FreshBanana` | 🍌 Plátano | Fresco |
| `FreshOrange` | 🍊 Naranja | Fresca |
| `FreshStrawberry` | 🍓 Fresa | Fresca |
| `RottenApple` | 🍎 Manzana | Podrida |
| `RottenBanana` | 🍌 Plátano | Podrido |
| `RottenOrange` | 🍊 Naranja | Podrida |
| `RottenStrawberry` | 🍓 Fresa | Podrida |

---

## 🛠️ Tecnologías y Dependencias

- **Lenguaje**: Kotlin 2.0.21
- **Arquitectura de UI**: Android View Binding / ConstraintLayout / Material Components 3
- **Cámara**: AndroidX CameraX `1.4.1` (Core, Camera2, Lifecycle, View)
- **Machine Learning**: `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`
- **Gradle**: Gradle 8.10.1 con Android Gradle Plugin (AGP) 8.10.1
- **Nivel de SDK**:
  - `minSdk`: 25 (Android 7.1)
  - `compileSdk`: 35 (Android 15)
  - `targetSdk`: 35

---

## 📁 Estructura del Proyecto

```text
app_frutas/
├── app/
│   ├── build.gradle.kts                # Dependencias (CameraX, ONNX Runtime)
│   ├── src/main/
│   │   ├── AndroidManifest.xml         # Permiso de cámara y características de hardware
│   │   ├── assets/
│   │   │   ├── effnet_b0_tl.onnx       # Modelo ONNX (EfficientNet-B0)
│   │   │   └── labels.txt              # Etiquetas de las 8 clases
│   │   ├── java/com/example/app_frutas/
│   │   │   ├── Clasificador.kt         # Preprocesamiento, Letterbox, Inferencia y Softmax
│   │   │   └── MainActivity.kt         # Integración CameraX, UX/UI y Linterna
│   │   └── res/
│   │       ├── drawable/               # Iconos vectoriales, formas glassmorphic y retícula
│   │       ├── layout/
│   │       │   └── activity_main.xml   # Layout principal UX/UI HUD
│   │       └── values/
│   │           ├── colors.xml          # Paleta de colores temáticos
│   │           └── themes.xml          # Tema Material 3 DayNight
├── gradle/
│   └── libs.versions.toml              # Catálogo de versiones de librerías
├── .gitignore                          # Configuración limpia para control de versiones
├── build.gradle.kts                    # Configuración raíz de plugins
└── README.md                           # Documentación técnica
```

---

## 🚀 Instalación y Puesta en Marcha

### Prerrequisitos
1. **Android Studio** (Koala / Ladybug o superior recomendado).
2. **Android SDK** con versión de compilación 35 instalada.
3. Dispositivo físico o emulador con cámara funcional (se recomienda dispositivo físico para probar con frutas reales).

### Pasos
1. Clona el repositorio:
   ```bash
   git clone https://github.com/tu-usuario/app_frutas.git
   ```
2. Abre la carpeta en **Android Studio**.
3. Permite que Gradle sincronice las dependencias automáticamente (`Sync Project with Gradle Files`).
4. Conecta tu dispositivo Android con **Depuración USB** activada.
5. Haz clic en **Run 'app'** (`Shift + F10`).

---

## 📦 Subir a GitHub (Control de Versiones)

Para publicar este proyecto limpio en tu repositorio de GitHub, ejecuta en la terminal de la raíz del proyecto:

```bash
# 1. Inicializar repositorio
git init

# 2. Agregar todos los archivos rastreados (.gitignore excluirá automáticamente cachés y binarios)
git add .

# 3. Crear el primer commit
git commit -m "feat: Lanzamiento inicial de FrutaScan AI con interfaz HUD y ONNX Runtime"

# 4. Configurar rama principal
git branch -M main

# 5. Vincular a tu repositorio remoto en GitHub
git remote add origin https://github.com/TU_USUARIO/TU_REPOSITORIO.git

# 6. Subir el código
git push -u origin main
```

---

## 📄 Licencia

Este proyecto se distribuye bajo la licencia **MIT**. Consulta el archivo `LICENSE` para más información.
