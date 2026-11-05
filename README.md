# Redo

Mod replay pour Minecraft : Redo enregistre tes parties, trouve tout seul les moments forts, propose des plans de caméra propres (travellings, orbites, survols, gros plans…) et monte une bande-annonce. Un éditeur de montage intégré permet de tout retoucher, et on peut demander des plans en écrivant ce qu'on veut, par exemple « orbite autour de moi pendant le combat ».

Redo s'inspire de ce que font Flashback et le replay de Lunar Client, sans reprendre leur code ni leur interface.

## Versions

| Minecraft | Chargeur | État |
|---|---|---|
| 1.8.9 | Forge 11.15.1.2318 | compilé et testé en jeu par la CI |
| 1.21.11 | Fabric Loader 0.17+ (sans Fabric API) | compilé et testé en jeu par la CI |
| 26.1, 26.2 | Fabric Loader 0.17+ (sans Fabric API) | compilés et testés en jeu par la CI |
| 26.3 | Fabric Loader 0.17+ (sans Fabric API) | compilé par la CI ; le test en jeu ne démarre pas encore sur l'écran virtuel de la CI (nouveau moteur de rendu de 26.3) |

Un enregistrement se relit avec la version de Minecraft qui l'a fait (le format des paquets change d'une version à l'autre).

## Installation

- **1.8.9** : mets `redo-<version>.jar` dans `.minecraft/mods` avec Forge 1.8.9.
- **1.21.11, 26.1, 26.2, 26.3** : installe Fabric Loader, puis mets le jar de ta version (`redo-<version>+1.21.11.jar`, `redo-<version>+26.1.jar`…) dans `.minecraft/mods`. Fabric API n'est pas nécessaire.

Les jars sont produits par la CI (onglet Actions du dépôt, un artefact `redo-<version de Minecraft>` par version).

## Utilisation

### Enregistrer

L'enregistrement démarre tout seul quand tu rejoins un serveur ou un monde solo, et s'arrête quand tu quittes. Un petit point rouge en haut de l'écran indique qu'il tourne.

- **F9** : début / fin d'un moment fort (Redo le mettra en avant dans la bande-annonce).
- **M** : pose un marqueur sur la timeline.

Les enregistrements vont dans `.minecraft/redo/recordings` (fichiers `.redo`). Si le jeu plante pendant un enregistrement, Redo le récupère au lancement suivant.

### Relire et monter

Le bouton **Redo** en haut à droite de l'écran titre ouvre la liste des enregistrements (Ouvrir, Renommer, Supprimer). À l'ouverture, Redo charge le monde enregistré, analyse la partie (combats, morts, constructions, chat, moments forts) et prépare un montage automatique.

L'éditeur a trois modes :

- **Montage** : aperçu, timeline des plans, inspecteur du plan choisi, propositions de plans, bande-annonce automatique, export.
- **Caméra libre** : tu te déplaces comme en spectateur avec tes touches de déplacement, saut et sneak, **K** ajoute une image clé, **Entrée** transforme les images clés en plan, **P** lecture / pause, flèches pour avancer, reculer et changer la vitesse.
- **Plein écran** : la vidéo seule, **Espace** pour lire, **F** ou **Échap** pour revenir.

Raccourcis du mode Montage : **Espace** lecture / pause, **← / →** une seconde (maj : cinq secondes, ctrl : une image), **Début / Fin**, **S** couper au curseur, **Ctrl+S** enregistrer le montage, **Suppr** supprimer le plan, **Ctrl+Z / Ctrl+Y** annuler / refaire, **+ / −** zoom de la timeline, **Tab** caméra libre, **F** plein écran.

Types de plans : plan large d'ouverture, survol drone, travelling avant, travelling arrière, travelling latéral, suivi de dos, orbite, grue, contre-plongée, plongée, plan épaule, gros plan visage, point de vue, panoramique, plan penché, ralenti, timelapse.

### Plans par prompt

Le champ en bas de l'éditeur accepte une demande en français ou en anglais, puis **Générer** :

- sans configuration, Redo comprend les demandes simples par mots-clés (« orbite autour de Steve pendant le combat », « drone shot of the base », « bande-annonce nerveuse de 30 s ») ;
- avec une clé d'API Claude dans `config/redo.json` (`"claudeApiKey"`), la demande est envoyée à Claude, qui comprend des demandes plus libres. Seul le texte de la demande et un résumé de la partie (joueurs, moments, durée) sont envoyés.

### Exporter

**Exporter** produit une vidéo dans `.minecraft/redo/exports` : MP4 (H.264 ou H.265), ProRes (avec transparence en 4444) ou séquence d'images PNG, en 720p, 1080p, 1440p, 4K ou vertical 1080×1920, à 24, 30 ou 60 images par seconde. L'export passe par ffmpeg : Redo le trouve s'il est installé, sinon il propose de le télécharger (Windows et macOS). Sous Linux, installe-le avec le gestionnaire de paquets.

## Réglages

`config/redo.json` : enregistrement automatique, sauvegarde automatique, touches (codes GLFW), dossiers des enregistrements et des vidéos, chemin de ffmpeg, clé et modèle Claude, vitesse de la caméra libre, affichage du point rouge.

## Limites connues

- En 1.8.9, revenir en arrière recharge le monde enregistré depuis le début, ce qui recharge aussi le pack de ressources du serveur s'il y en a un.
- Juste après un saut dans le temps, les entités se recalent sur leur position au lieu de glisser jusqu'à elle.
- Les paquets propres aux mods du serveur sont rejoués tels quels : ce qu'ils affichent n'apparaît que si le même mod est installé.
- Un enregistrement commence pendant la connexion au serveur : le début de la timeline (avant l'arrivée dans le monde) reste figé sur la première image du monde.
- En 26.3, le coup de bras de ton propre joueur n'est pas rejoué (les autres joueurs, si).

## Développement

- `core/` : tout ce qui ne dépend pas de Minecraft (format `.redo`, enregistreur, analyse des moments, plans automatiques, bande-annonce, éditeur et interface, export ffmpeg, prompt). Java 8, tests JUnit : `cd core && ./gradlew test`.
- `forge-1.8.9/` : adaptateur 1.8.9 (Forge + Mixin). `cd forge-1.8.9 && ./gradlew build`.
- `fabric/` : adaptateur des versions récentes (Fabric + Mixin, Stonecutter pour les variantes de version ; ce qui change d'une version à l'autre est regroupé dans `Compat.java`). `cd fabric && ./gradlew :1.21.11:buildAndCollect` (ou `:26.1:`, `:26.2:`, `:26.3:`).

La CI (`.github/workflows/build.yml`) compile chaque version, lance les tests du cœur, puis fait un vrai test en jeu pour chaque version : elle démarre un serveur vanilla de test (dont elle accepte l'EULA de Minecraft, le temps du job), lance le client sous un écran virtuel, enregistre quelques secondes pendant que le serveur fait apparaître des animaux et construit une tour, puis rouvre l'enregistrement, le lit, revient en arrière, exporte la bande-annonce et quitte. Les captures d'écran, les journaux et la vidéo sont dans l'artefact `e2e-<version>` du job.

## Licence

Tous droits réservés pour l'instant (licence à choisir).
