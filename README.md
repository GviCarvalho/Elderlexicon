# Elder Lexicon

Elder Lexicon is a magic system / mod toolkit that implements an elemental-energy economy (UMU — Universal Magical Unit) and an internal "Vita" (life-as-energy) model for use in gameplay and mod mechanics.

This repository contains the core systems, design docs and implementation scaffolding for:
- UMU — Universal Magical Unit (energy accounting for spells and effects)
- Vita — life-as-elemental-energy with internal element composition and consequences
- Spell rune system: sources, functions and filters
- Tools and reference documentation for balancing and implementing magical mechanics

Documentation
- ReadmeUMU.md — Details the UMU system, resource conversion order, runes, functions and cost calculations.
- ReadmeVITA.md — Details Vita (HP as elemental energy), composition, effects of imbalance and technical notes for implementation.

Quick links
- ReadmeUMU.md: ./ReadmeUMU.md
- ReadmeVITA.md: ./ReadmeVITA.md
- License: ./LICENSE.txt

Features
- Clear, document-backed energy accounting for spells and magical actions (XP, Saturation, HP → UMU)
- Rune/funtion/filter taxonomy with cost rules and examples
- Vita modeled as a mixture of four elemental components (Aqua, Aura, Igni, Firmo) with drain/imbalance mechanics
- Example calculations and conversion rules to make balancing straightforward

Requirements
- Java JDK (recommended 17+)
- Gradle (wrapper included: use ./gradlew or gradlew.bat)
- Minecraft mod loader and target Minecraft version depend on the implementation; check build.gradle for exact targets

Build (from repo root)
1. Clone the repository
   git clone https://github.com/GviCarvalho/Elderlexicon.git
2. Build using the included Gradle wrapper:
   - On macOS / Linux: ./gradlew build
   - On Windows: gradlew.bat build
3. Output JARs (if any) will be under build/libs/

Usage
- The repository contains design docs and code scaffolding. Consult ReadmeUMU.md and ReadmeVITA.md to understand the systems and adjust values for balancing.
- Integration with a specific modding framework (Fabric / Forge) will depend on the project's current targets — see build.gradle and src/ for implementation details.

Contributing
- Contributions, issues and improvements are welcome.
- Please open issues describing bug reports or feature requests.
- If submitting code changes, follow the existing project style, include tests where applicable and document new behavior in the relevant README file.

License
- See LICENSE.txt in the repository root for licensing details.

Contact / Author
- Repository owner: GviCarvalho
- Use GitHub issues or pull requests for questions, requests, or contributions.

Português — Resumo rápido
- Elder Lexicon implementa UMU (Unidade Mágica Universal) para custeio de feitiços e Vita (HP como energia elemental).
- Veja ReadmeUMU.md e ReadmeVITA.md para documentação detalhada em português.
- Para construir: use o wrapper do Gradle (./gradlew build). Veja build.gradle para a versão alvo e dependências.

If you want, I can:
- Add a short examples section with common spell cost computations.
- Generate badges (build / license).
- Create CONTRIBUTING.md and CODE_OF_CONDUCT.md templates.
