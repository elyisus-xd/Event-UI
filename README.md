# EventUI

Sistema de UI y misiones completamente configurable para Minecraft 1.21.1 (Fabric).

## 🎯 Arquitectura

EventUI está diseñado con una arquitectura desacoplada de tres capas:

- **eventui-common**: Contratos e interfaces compartidas.
- **eventui-core**: Plugin de servidor (Paper/Spigot) con lógica de misiones, estados y eventos.
- **eventui-fabric**: Mod de cliente (Fabric) con adaptador y renderizado de UI.

## ✨ Características

- **Sistema de misiones configurable**: Eventos, objetivos, recompensas y dependencias
- **Árboles de habilidades**: Sistema completo de skill trees con ramas exclusivas y requisitos
- **UI configurable**: Sistema de UI basado en YAML con animaciones, hover effects y tooltips
- **Quest Tracker HUD**: Sistema de HUD persistente para seguimiento de misiones con notificaciones
- **Data bindings**: Variables dinámicas en UI ({{quest.icon}}, {{event.display_name}}, etc.)
- **Animaciones**: Zoom, shake, bounce, rotate, swing, float, wave, heartbeat, jelly, spin_3d
- **Tooltips avanzados**: Soporte para items, recetas, entidades, imágenes y texto personalizado
- **Sistema de notificaciones**: Plantillas configurables con animaciones

## 🚀 Requisitos

### Servidor (Plugin)
- **Java 21**
- **Paper/Spigot 1.21.1**

### Cliente (Mod)
- **Java 21**
- **Minecraft 1.21.1**
- **Fabric Loader 0.18.4+**
- **Fabric API 0.108.0+**

## 🛠️ Compilación

El proyecto tiene dos componentes que se compilan por separado:

### Plugin de Servidor (eventui-core)
```bash
./gradlew eventui-core:shadowJar
```
Genera: `eventui-core/build/libs/eventui-core-1.0.2-all.jar`

Este JAR incluye las dependencias necesarias:
- SnakeYAML (para configuración YAML)
- Adventure Text (para MiniMessage y serialización de texto)
- Gson (para JSON)

Las dependencias son relocalizadas internamente para evitar conflictos con otros plugins.

### Mod de Cliente (eventui-fabric)
```bash
./gradlew eventui-fabric:build
```
Genera: `eventui-fabric/build/libs/eventui-fabric-1.0.2.jar`

Este JAR incluye automáticamente las dependencias de eventui-core y eventui-common.

## 🎮 Desarrollo
- **Ejecutar cliente de desarrollo:**
```bash
./gradlew eventui-fabric:runClient
```
- **Ejecutar servidor de desarrollo:**

```bash
./gradlew eventui-fabric:runServer
```

## 📁 Estructura del proyecto
```
eventui/
├── eventui-common/      
├── eventui-core/        
└── eventui-fabric/
```

## 📝 Licencia
 MIT
## 👤 Autor
elyisusxd