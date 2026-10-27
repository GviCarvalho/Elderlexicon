<p align="center">
<img width="600" height="338" alt="MyMovie-ezgif com-optimize" src="https://github.com/user-attachments/assets/2a0f99a0-bb8e-42b2-9624-a4d1c1684e84" />
</p>

# minecraft-builder-skill

Give your agent Minecraft superpowers: it can design builds, generate JavaScript build recipes, export vanilla `.nbt` structures, and open interactive previews you can inspect before placing anything in-game.

## Requirements

- Node.js 20.19 or newer
- `npx`
- An agent that can load Skills CLI skills

## Setup

Install it into your agent's skills directory:

```bash
npx skills install mattzh72/minecraft-builder-skill
```

To target a specific agent, replace `claude-code` with the agent you use:

```bash
npx skills install mattzh72/minecraft-builder-skill --agent claude-code
```

Restart or reload your agent so it can discover the skill. On first use, the skill instructs your agent to install its Node dependencies from `package.json` if they are missing.

## Use

Ask your agent to use the installed skill:

```text
Use minecraft-builder-skill to make a floating crystal observatory, export the .nbt file, and open a night-mode preview.
```
