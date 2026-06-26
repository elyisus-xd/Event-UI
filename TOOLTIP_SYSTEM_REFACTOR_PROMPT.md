# Refactor de Sistema de Tooltips Avanzados — Prompt de contexto

**Estado actual:** Sistema de tooltips con soporte para RECIPE, ITEM, ENTITY, IMAGE, TEXT, SEPARATOR. El tipo RECIPE es un monolito que solo soporta Shaped/Shapeless, con posiciones y dimensiones hardcodeadas.

**Objetivo:** Pulir completamente el sistema, hacerlo modular, extensible a todos los tipos de receta (smithing, furnace, blasting, smoker, potion brewing), configurable en YAML, y mejorar experiencia de usuario.

---

## Código actual (referencia)

### RecipeTooltipComponent.java
- Record con `recipe` y `customFrame` (String, solo una textura)
- Solo renderiza `ShapedRecipe` explícitamente; todo lo demás fallback a shapeless
- Posiciones totalmente hardcodeadas:
  - Frame: `x, y` (crafting grid 3×3)
  - Arrow: `x + 61` (separados del grid)
  - Output: `x + 95` (muy separado)
- Dimensiones fijas: `getHeight() = 76`, `getWidth() = 130`
- Método `renderVanillaFrame()` extrae textura de crafting_table vanilla

### TooltipRenderer.java
- Renderiza secciones de tooltip
- Caso `RECIPE`: busca la receta, instancia `RecipeTooltipComponent`, llama `renderImage()`
- Fondo del tooltip: hardcodeado en `renderTooltipBackground()` (cuadro morado/negro con borde gradiente)
- Parsing manual de receta: `recipeId.trim()` directamente del YAML

### Uso actual en YAML
```yaml
children:
  - id: "recipe_tooltip"
    type: TOOLTIP
    properties:
      render_type: "advanced"
      content: |
        recipe: minecraft:diamond_chestplate [frame:eventui:textures/ui/widgets/crafting_frame.png]
```

---

## Cambios requeridos — Por etapas

### ETAPA 1: Refactor a patrón Factory + Strategy (Complejidad: MEDIA)

**Objetivo:** Soportar múltiples tipos de receta (Shaped, Shapeless, Smithing, Furnace, Blasting, Smoker, BrewingStand).

**Cambios de código:**

1. **Crear interfaz `RecipeRenderer`**
   ```java
   public interface RecipeRenderer {
       int getHeight();
       int getWidth(Font font);
       void renderRecipe(GuiGraphics graphics, Font font, int x, int y);
   }
   ```

2. **Implementaciones concretas** (una por tipo de receta):
   - `ShapedRecipeRenderer implements RecipeRenderer` — renderiza grid 3×3, ingredientes, arrow, output
   - `ShapelessRecipeRenderer` — renderiza grid 3×3 (rellenando de arriba-izquierda hacia abajo-derecha)
   - `SmithingRecipeRenderer` — renderiza layout: [template] [material] → [result] (2 slots entrada, 1 resultado)
   - `FurnaceRecipeRenderer` — renderiza: [input] → [output] (1 slot entrada, 1 resultado)
   - `BlastingRecipeRenderer`, `SmokerRecipeRenderer` — similar a furnace
   - `BrewingRecipeRenderer` — renderiza layout especial de brewing (3 botellas arriba, 1 ingrediente abajo, → flecha → resultado)

3. **Clase `RecipeRendererFactory`**
   ```java
   public class RecipeRendererFactory {
       public static RecipeRenderer create(Recipe<?> recipe, RecipeGridConfig config) {
           // Detecta el tipo real de recipe y devuelve el renderer apropiado
           // Fallback: ShapelessRecipeRenderer si el tipo es desconocido
       }
   }
   ```

4. **Reescribir `RecipeTooltipComponent`** → pasar de un record a una clase normal:
   ```java
   public class RecipeTooltipComponent implements ClientTooltipComponent {
       private Recipe<?> recipe;
       private RecipeGridConfig gridConfig;
       private RecipeRenderer renderer;
       
       public RecipeTooltipComponent(Recipe<?> recipe, RecipeGridConfig config) {
           this.recipe = recipe;
           this.gridConfig = config;
           this.renderer = RecipeRendererFactory.create(recipe, config);
       }
       
       @Override
       public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
           if (gridConfig.isShowGridFrame()) {
               renderGridFrame(graphics, x, y);
           }
           renderer.renderRecipe(graphics, font, x, y);
       }
   }
   ```

**Beneficios:**
- Cada tipo de receta tiene su propio renderer, sin lógica complicada en un solo método
- Nuevo tipo de receta = nueva clase, no modificar factory existente
- Fácil de testear en aislamiento

---

### ETAPA 2: Configuración de grid en YAML (Complejidad: BAJA)

**Objetivo:** Permitir que el YAML defina cómo se renderiza el grid (espaciado, posiciones, si mostrar frame, etc.).

**Cambios de código:**

1. **Crear clase `RecipeGridConfig`**
   ```java
   public class RecipeGridConfig {
       private boolean showGridFrame;           // default: true
       private int slotSpacing;                 // default: 18 (ancho/alto de un slot)
       private int arrowOffsetX;                // default: 61
       private int arrowOffsetY;                // default: 20
       private int outputOffsetX;               // default: 95
       private int outputOffsetY;               // default: 18
       private String gridFrameTexture;         // ej: eventui:textures/ui/widgets/crafting_frame.png
       private int gridFrameWidth;              // default: 54 (para 3×3)
       private int gridFrameHeight;             // default: 54
       
       // getters, setters, builder
   }
   ```

2. **Parsear YAML expandido en `TooltipRenderer`**
   ```yaml
   case RECIPE -> {
       String recipeId = section.getData().get("recipe");
       String showFrame = section.getData().getOrDefault("show_grid_frame", "true");
       String slotSpacing = section.getData().get("slot_spacing");
       String arrowOffset = section.getData().get("arrow_offset");  // "x,y" format
       String outputOffset = section.getData().get("output_offset"); // "x,y" format
       
       RecipeGridConfig config = new RecipeGridConfig.Builder()
           .showGridFrame(Boolean.parseBoolean(showFrame))
           .slotSpacing(parseIntSafe(slotSpacing, 18))
           // ... parsear offsets
           .build();
       
       // cargar receta y renderizar
   }
   ```

3. **YAML esperado después**
   ```yaml
   children:
     - id: "recipe_tooltip"
       type: TOOLTIP
       properties:
         render_type: "advanced"
         content: |
           section_type: RECIPE
           recipe: minecraft:diamond_chestplate
           show_grid_frame: true
           slot_spacing: 18
           arrow_offset: "61,20"
           output_offset: "95,18"
           grid_frame_texture: "eventui:textures/ui/widgets/crafting_frame.png"
   ```
   O más simple (valores por defecto):
   ```yaml
   content: |
     section_type: RECIPE
     recipe: minecraft:diamond_chestplate
   ```

**Beneficios:**
- Admins pueden customizar el look sin tocar código
- Fácil de cambiar offsets sin recompilar
- Soporte para diferentes "estilos" de grid configurables

---

### ETAPA 3: Opción de ocultar fondo del tooltip (Complejidad: BAJA)

**Objetivo:** Cumplir la solicitud original — poder renderizar SOLO el grid sin el cuadro morado/negro.

**Cambios de código:**

1. **Expandir `TooltipConfig`** para agregar:
   ```java
   private boolean showTooltipBackground; // default: true
   ```

2. **En `TooltipRenderer.renderAdvancedTooltipNative()`**, condicionar:
   ```java
   if (config.isShowTooltipBackground()) {
       renderTooltipBackground(graphics, x, y, dims.width, dims.height);
   }
   ```

3. **YAML**:
   ```yaml
   properties:
     render_type: "advanced"
     show_tooltip_background: false
     content: |
       section_type: RECIPE
       recipe: minecraft:diamond_chestplate
   ```

**Beneficios:**
- UI limpia (solo grid + flecha + resultado visible)
- 2 líneas de código, 0 complejidad

---

### ETAPA 4: Caché de lookups de receta (Complejidad: BAJA)

**Objetivo:** No buscar `level.getRecipeManager().byKey()` cada vez que se renderiza el tooltip (puede ocurrir 60 veces por segundo si el usuario mantiene el mouse sobre un botón).

**Cambios de código:**

1. **En `RecipeTooltipComponent` o nueva clase `RecipeCache`**:
   ```java
   private static final Map<ResourceLocation, Recipe<?>> recipeCache = new ConcurrentHashMap<>();
   
   public static Recipe<?> getRecipe(ResourceLocation id, Minecraft client) {
       return recipeCache.computeIfAbsent(id, loc -> {
           if (client.level == null) return null;
           var holder = client.level.getRecipeManager().byKey(loc);
           return holder.isPresent() ? holder.get().value() : null;
       });
   }
   ```

2. **Invalidar caché en reload de servidor**:
   ```java
   // En ClientEventBridge o handler de reload
   RecipeCache.clear();
   ```

**Beneficios:**
- 60 fps en lugar de búsquedas O(n) en recipeManager
- Mínimo overhead de memoria (solo recetas ya pedidas)

---

### ETAPA 5: Soporte para múltiples recetas (Complejidad: MEDIA-ALTA)

**Objetivo:** Si un item tiene 5 formas de craftearse, mostrar "1/5" y permitir scroll/flechas para cambiar.

**Cambios de código:**

1. **Modificar `RecipeTooltipComponent`** para holdar lista:
   ```java
   private List<Recipe<?>> matchingRecipes;
   private int currentRecipeIndex = 0;
   ```

2. **Botones de navegación** (pequeñas flechas dentro del tooltip):
   ```java
   renderNavigationArrows(graphics, x, y);
   renderRecipeCounter(graphics, x, y, currentRecipeIndex, matchingRecipes.size());
   ```

3. **Input handling**: detectar clicks en flechas (← →) y cambiar `currentRecipeIndex`

4. **Búsqueda de recetas matching**: en lugar de `byKey(id)`, buscar todas con:
   ```java
   client.level.getRecipeManager().getRecipes().stream()
       .filter(h -> h.value().getResultItem(...).getItem() == targetItem)
       .collect(Collectors.toList());
   ```

**Beneficios:**
- Muestra todas las opciones de crafteo para un item
- UX completa sin salir del tooltip

---

### ETAPA 6: QoL adicionales (Complejidad: VARIABLE)

A considerar para fases posteriores:

- **Preview en tiempo real**: resaltar ingredientes que el jugador no tiene (color oscuro/rojo)
  - Requiere: comparación del inventario del jugador contra los ingredientes
  - Complejidad: MEDIA

- **Escalado dinámico**: si tooltip va a salirse de pantalla, reducir grid automáticamente
  - Complejidad: BAJA (modificar `RecipeGridConfig.gridFrameWidth/Height` antes de renderizar)

- **Soporte para "sub-steps"**: recetas con prerequisitos (crafting multibloque)
  - Complejidad: ALTA (arquitectura completamente distinta)

- **Animación de ingredientes**: girar, colorear, animar items en el grid
  - Complejidad: MEDIA (integrar con sistema de animaciones existente)

---

## Plan de implementación recomendado

**Fase 1** (MVP — 2-3 horas):
- ✅ Etapa 1: Factory + Strategy para 6 tipos de receta
- ✅ Etapa 2: Config en YAML
- ✅ Etapa 3: Toggle de fondo tooltip

**Fase 2** (Polish — 1-2 horas):
- ✅ Etapa 4: Caché de lookups
- ✅ Etapa 6.1: Preview en tiempo real (opcional, si el esfuerzo es bajo)

**Fase 3** (Opcionalmente):
- ✅ Etapa 5: Múltiples recetas (complejo, considerar después de publicación v1.0)
- ✅ Etapa 6.2+: Animaciones, escalado dinámico (nice-to-have)

---

## Criterios de éxito

- ✅ Tooltip de receta renderiza correctamente para Shaped, Shapeless, Smithing, Furnace
- ✅ Posiciones de arrow y output son ajustables sin recompilar
- ✅ Se puede ocultar el fondo morado/negro del tooltip
- ✅ Se puede ocultar solo el frame del grid (mostrar solo ingredientes + resultado)
- ✅ YAML no requiere syntax complicada (soporta tanto expanded como shorthand)
- ✅ Sin lag (caché de recetas activo)
- ✅ Código es mantenible (Factory, cada tipo en su propia clase)
