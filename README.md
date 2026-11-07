# COOL VOICE — Application Android de Transformation Vocale en Temps Réel

> **Slogan :** Transforme ta voix. Libère ta créativité.  
> **Conception et Développement :** Solohery RAKOTOARISOA  
> **Version :** 1.0.0 (Native Android / Kotlin / Jetpack Compose)  
> **Fonctionnement :** 100% Hors-ligne, local, confidentiel et sans abonnement.

---

## 1. Diagramme d'Architecture Logicielle & Flux Audio

L'application est conçue pour garantir une latence minimale, zéro allocation d'objets dans la boucle temps réel (zero GC churn) et une protection acoustique contre les boucles de rétroaction (larsen) :

```text
               ┌───────────────────────────────┐
               │    MICROPHONE DU TÉLÉPHONE    │
               └───────────────┬───────────────┘
                               │ (PCM 16-bit 44.1/48 kHz)
                               ▼
               ┌───────────────────────────────┐
               │      AUDIORECORD (CAPTURE)    │
               │   Buffer circulaire low-delay │
               └───────────────┬───────────────┘
                               │
                               ▼
               ┌───────────────────────────────┐
               │      MOTEUR DSP LOCAL         │
               │  ┌─────────────────────────┐  │
               │  │ Granular Pitch Shifter  │  │ (Transposition temps réel)
               │  ├─────────────────────────┤  │
               │  │ Biquad IIR Direct Form  │  │ (Filtres Passe-Bas/Haut/Bande/EQ)
               │  ├─────────────────────────┤  │
               │  │ Circular Delay Lines    │  │ (Écho & réverbération spatiale)
               │  ├─────────────────────────┤  │
               │  │ Oscillateurs LFO / Ring │  │ (Modulation robot/alien/trémolo)
               │  ├─────────────────────────┤  │
               │  │ Wave-shaping Limiter    │  │ (Protection saturation douce)
               │  └─────────────────────────┘  │
               └───────┬───────────────┬───────┘
                       │               │
        (Signal métamorphosé)          │ (Peak Amplitude)
                       ▼               ▼
        ┌───────────────────────┐  ┌───────────────────────────────┐
        │  CONTRÔLE DU VOLUME   │  │   VISUALISATEUR TEMPS RÉEL    │
        │  + PROTECTION LARSEN  │  │  (Waveform Animated Canvas)   │
        └───────┬───────┬───────┘  └───────────────────────────────┘
                │       │
    ┌───────────┴┐     ┌┴─────────────────────────────────────────┐
    ▼            │     ▼                                          ▼
┌──────────────┐ │ ┌───────────────────────┐ ┌────────────────────────────┐
│  AUDIOTRACK  │ │ │    AUDIO RECORDER     │ │  BASE DE DONNÉES ROOM      │
│  (Écoute     │ │ │ Flux direct en WAV    │ │  (Métadonnées, durées,     │
│   Casque /   │ │ │ PCM standard 16-bit   │ │   effets, horodatage)      │
│   Haut-parl.)│ │ └───────────────────────┘ └────────────────────────────┘
└──────────────┘ │
                 ▼
     ┌───────────────────────┐
     │  BIBLIOTHÈQUE LOCALE  │
     │  Lecture, renommage,  │
     │  suppression, partage │
     └───────────────────────┘
```

---

## 2. Les 12 Effets Vocaux Fonctionnels

Chaque effet applique un traitement acoustique et numérique réel :

1. **ROBOT :** Modulation en anneau (Ring Modulation) via oscillateur porteuse + saturation douce d'overdrive + filtre passe-bande vocal mid-range (300 Hz - 3 400 Hz).
2. **FEMME :** Transposition de hauteur vers l'aigu (Pitch Shift ~1.35x) + égalisation formantique (hausse de présence à 2 600 Hz et coupe-bas des résonances thoraciques).
3. **HOMME :** Transposition de hauteur vers le grave (Pitch Shift ~0.75x) + amplification de résonance pectorale à 220 Hz et adoucissement des aigus.
4. **ENFANT :** Transposition vive (Pitch Shift ~1.45x) + rehaussement de clarté et suppression des sous-harmoniques graves.
5. **MONSTRE :** Transposition profonde (Pitch Shift ~0.60x) + forte distorsion douce (wave-shaping) + modulation sub-harmonique grognement (9 Hz) + basses massives.
6. **GRAVE :** Transposition profonde nette (Pitch Shift ~0.70x) + filtre passe-bas chaleureux (2 200 Hz) pour une voix radio d'outre-tombe sans distorsion excessive.
7. **AIGUË :** Transposition aiguë ludique (Pitch Shift ~1.60x) + filtre passe-haut et brillance cristalline.
8. **ÉCHO :** Double ligne à retard circulaire (tap principal 240 ms + tap secondaire 120 ms) avec réinjection et amortissement spatialisé.
9. **EXTRATERRESTRE :** Intermodulation à double oscillateur sinusoïdal (280 Hz et 435 Hz) combinée à un trémolo spatial dynamique (6.5 Hz).
10. **DESSIN ANIMÉ :** Transposition aiguë (Pitch Shift ~1.50x) couplée à un vibrato sinusoïdal de fréquence (5.5 Hz) pour un rendu bondissant et comique.
11. **PERSONNE ÂGÉE :** Transposition subtile (~0.92x) avec trémolo et tremblement vocal naturel (3.8 Hz) et limitation de bande passante façon gramophone/radio vintage.
12. **BÉBÉ :** Transposition très haute (Pitch Shift ~1.75x) avec micro-ondulations vocales douces et rehaussement expressif.

---

## 3. Structure du Projet

```text
app/src/main/
├── AndroidManifest.xml                  (Permissions RECORD_AUDIO, FileProvider)
├── java/com/example/
│   ├── MainActivity.kt                  (Navigation M3, gestion des permissions, barres système)
│   ├── MainViewModel.kt                 (Coordination audio, persistance et UI)
│   ├── audio/
│   │   ├── AudioEngine.kt               (AudioRecord, AudioTrack, thread temps réel)
│   │   ├── AudioRecorder.kt             (Écriture PCM vers fichier WAV normalisé)
│   │   ├── AudioPlayer.kt               (Lecteur audio intégré avec contrôle temporel)
│   │   ├── AudioDeviceHelper.kt         (Détection casque filaire / Bluetooth / haut-parleur)
│   │   ├── AudioVisualizerState.kt      (Tampon d'amplitudes pour visualiseur Compose)
│   │   └── effects/
│   │       ├── VoiceEffect.kt           (Enumération des 12 effets et icônes)
│   │       ├── DspProcessor.kt          (Moteur de traitement numérique complet)
│   │       ├── GranularPitchShifter.kt  (Algorithme Overlap-Add à gain unitaire)
│   │       ├── BiquadFilter.kt          (Filtres IIR Direct Form)
│   │       └── DelayLine.kt             (Ligne à retard circulaire interpolée)
│   ├── database/
│   │   ├── RecordingEntity.kt           (Entité Room pour les enregistrements)
│   │   ├── RecordingDao.kt              (DAO SQLite Flow / Coroutines)
│   │   └── AppDatabase.kt               (Base de données Room)
│   ├── settings/
│   │   └── AppSettings.kt               (DataStore préférences : Thème, Audio, Larsen)
│   └── ui/
│       ├── theme/                       (Palette violet / cyan / neutre, Dark & Light)
│       ├── components/                  (WaveformVisualizer, EffectSelectorCard, Dialogs)
│       └── screens/
│           ├── HomeScreen.kt            (Accueil, accès rapide, statistiques)
│           ├── StudioScreen.kt          (Écoute directe, curseurs, enregistrement)
│           ├── LibraryScreen.kt         (Bibliothèque, lecteur, renommage, suppression, partage)
│           └── SettingsScreen.kt        (Thème, échantillonnage, anti-larsen, à propos)
```

---

## 4. Instructions de Compilation et Installation

### Prérequis
- Android SDK 34 ou supérieur (minSdk = 24, supporte Android 7.0 à 15+).
- Java 17 ou 21.

### Commandes Gradle
- **Lancer les tests unitaires et Robolectric :**
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Compiler l'APK de débogage :**
  ```bash
  gradle :app:assembleDebug
  ```
  L'APK généré sera situé dans `app/build/outputs/apk/debug/app-debug.apk`.

---

## 5. Guide de Résolution des Erreurs et Conseils d'Utilisation

1. **Microphone et Permissions :**
   - Si aucun son n'est capté, vérifiez que l'autorisation « Microphone » a été accordée dans les paramètres de votre téléphone. L'application affiche automatiquement un dialogue pédagogique expliquant la nécessité de cette permission.
2. **Sifflement / Effet Larsen :**
   - L'effet Larsen est un phénomène physique inévitable lorsqu'un haut-parleur émet du son à proximité immédiate d'un microphone sensible. Pour un confort d'écoute optimal, branchez des **écouteurs filaires ou un casque Bluetooth**.
   - Lorsque le haut-parleur intégré est utilisé, la protection anti-larsen de COOL VOICE bride automatiquement le gain pour préserver vos oreilles.
3. **Appels téléphoniques et WhatsApp :**
   - Conformément aux règles strictes de sécurité et de confidentialité du système d'exploitation Android, aucune application tierce standard ne peut intercepter ou modifier en direct le flux vocal des appels GSM, WhatsApp, Telegram ou Messenger. COOL VOICE respecte la politique de sécurité Google Play et ne propose aucun faux bouton trompeur.
