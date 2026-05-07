# EventUI

Sistema de UI y misiones completamente configurable para Minecraft 1.21.1 (Fabric).

## 🎯 Arquitectura

EventUI está diseñado con una arquitectura desacoplada de tres capas:

- **eventui-common**: Contratos e interfaces compartidas.
- **eventui-core**: Lógica de misiones, estados y eventos sin depender de minecraft.
- **eventui-fabric**: Adaptador Fabric + renderizado de UI.

## 🚀 Requisitos

- **Java 21**
- **Minecraft 1.21.1**
- **Fabric Loader 0.16.9+**
- **Fabric API 0.108.0+**

## 🛠️ Compilación

```bash
./gradlew build
```

El archivo del mod se genera en: ```eventui-fabric/build/libs/eventui-fabric-0.1.0-SNAPSHOT.jar```

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