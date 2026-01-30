# Visual UI Mockup Description

This document describes the visual appearance of the transformed UI.

## 🖼️ Main Screen Layout

```
┌─────────────────────────────────────────┐
│ [Settings]                              │ ← Top-right button
│                                         │
│                                         │
│                                         │
│          MAP AREA                       │
│       (MapLibre View)                   │
│                                         │
│    Route lines in Deep Marine           │
│    Transport icons: 🚌 🚊 🚆           │
│                                         │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ 🔍 Rechercher une destination...  │  │ ← Persistent search bar
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

## 📊 Loading Screen

```
┌─────────────────────────────────────────┐
│                                         │
│    ┌─────────────────────────────┐     │
│    │  Initialisation             │     │
│    │                             │     │
│    │  ✓ Chargement de la carte  │     │ ← Completed (checkmark)
│    │                             │     │
│    │  ⊙ Analyse des données de  │     │ ← In progress (spinner)
│    │    transport                │     │
│    │                             │     │
│    │  [ ] Construction du réseau │     │ ← Pending
│    │                             │     │
│    │  [ ] Optimisation des       │     │ ← Pending
│    │      trajets                │     │
│    │                             │     │
│    │  ──────────────────         │     │ ← Progress bar (25%)
│    │  1 / 4 étapes complétées    │     │
│    └─────────────────────────────┘     │
│                                         │
└─────────────────────────────────────────┘
```

## 🎨 Color Application

### Deep Marine Blue (#1C3F7A)
- Primary buttons
- Search icon
- Map route lines
- Transport icons (Bus, Tram, Train)
- Active text
- Step checkmarks
- Progress indicators

### Slate Gray (#2C3E50 - #34495E)
- Secondary surfaces
- Backgrounds
- Inactive elements

### Surface Colors
- Card backgrounds: #ECF0F1 (light mode) / #263238 (dark mode)
- Main background: #ECF0F1 (light mode) / #263238 (dark mode)

## 🔤 Typography Examples

### Headings
**Initialisation** - Headline Small (24sp, Deep Marine)
**Settings** - Title Medium (16sp)

### Body Text
**Chargement de la carte** - Body Medium (14sp, sans-serif)
**1 / 4 étapes complétées** - Body Small (12sp, gray)
**Rechercher une destination...** - Body Large (16sp, placeholder)

## 🚌 Transport Icons

All icons are 24dp vector drawables in Deep Marine blue:

**Bus Icon (🚌)**
```
   ┌─────┐
   │ ◯ ◯ │  ← Windows
   │     │
   │ ● ● │  ← Wheels
   └─────┘
```

**Tram Icon (🚊)**
```
    /─\
   ┌───┐
   │ ◯ │ ◯│  ← Windows (split)
   │───│───│
   │ ● │ ● │  ← Wheels
   └───┴───┘
```

**Train Icon (🚆)**
```
    ─┬─
   ┌───┐
   │ ◯ │ ◯│  ← Windows
   │───│───│
   │ ● │ ● │  ← Wheels
   └───┴───┘
```

## 🔍 Search Bar Details

**Shape**: Rounded rectangle with 28dp corner radius
**Elevation**: 3dp tonal + 8dp shadow
**Height**: ~56dp
**Padding**: 16dp horizontal, 8dp vertical
**Icon**: Material Icons Search (Deep Marine)
**Background**: Surface color (white/dark gray)

## 🗺️ Map Styling

**Route Border**: 8px width, #0A1F3D (dark Deep Marine)
**Route Center**: 5px width, #1C3F7A (Deep Marine)
**Route Caps**: Rounded
**Route Joins**: Rounded

**Labels**:
- Font: Sans-serif
- Size: 12sp (stops), 14sp (stations)
- Color: #1C3F7A (Deep Marine)
- Halo: 2px white outline

## 📱 Responsive Behavior

### Portrait Mode (Standard)
- Search bar: Full width minus 16dp padding each side
- Loading card: Max width 400dp, centered
- Map: Fills entire screen

### Landscape Mode
- Same proportions
- Search bar remains at bottom
- Loading card remains centered

## 🌓 Dark Mode

**Automatic switching** based on system preference:

**Dark Color Scheme**:
- Primary: #2B74FF (lighter Deep Marine)
- Background: #263238 (dark slate)
- Surface: #263238 (dark slate)
- On-surface text: #ECF0F1 (light gray)
- Cards: Slightly elevated from background

## ✨ Animations

1. **Loading spinner**: Continuous rotation on current step
2. **Progress bar**: Smooth fill animation (spring)
3. **Checkmarks**: Instant appearance on step completion
4. **Search bar**: Subtle focus animation
5. **Route drawing**: MapLibre default animation

## 🎯 Touch Targets

All interactive elements follow Material Design minimum:
- Buttons: 48dp × 48dp minimum
- Search bar: 56dp height
- Icon buttons: 48dp × 48dp
- List items: 48dp minimum height

## 📐 Spacing

**Consistent 8dp grid**:
- Small gaps: 8dp
- Medium gaps: 16dp
- Large gaps: 24dp
- Section padding: 24dp
- Screen edges: 16dp

## 🎭 Elevation Hierarchy

1. **Search bar**: 8dp shadow (highest, always accessible)
2. **Loading card**: 4dp shadow
3. **Map**: 0dp (base layer)
4. **Dialogs**: 24dp shadow (when shown)

This creates clear visual hierarchy and depth perception.
