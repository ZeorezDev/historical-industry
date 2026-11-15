# Historical Industry

A Forge mod for industrial production on Historical Nations Core land. Minecraft 1.20.1, Forge 47.4.28, Java 17. Install Historical Industry, Historical Nations Core **0.6.0** and Open Parties and Claims **0.33.0** on the server and clients.

## Starting a factory

Claim a chunk through Core, then use `/plot set type industrial`. A machine inside a Quarter follows that Quarter's access rules. Place a coal generator up to four blocks from a processor on the same straight axis, within the same parcel. Supply coal and place each recipe ingredient in its own input slot in the order below.

| Machine | Inputs per cycle | Output | Ticks | FE per tick |
| --- | --- | --- | ---: | ---: |
| Coal generator | 1 coal | 4,800 FE | 200 | +24 |
| Industrial furnace | 1 iron ingot, 1 coal | 1 steel ingot | 80 | −12 |
| Machine workshop | 1 steel ingot, 1 copper ingot | 1 machine parts | 100 | −16 |
| Textile factory | 1 wool, 1 string | 2 textile | 60 | −8 |

Right-click a machine to open its inventory. The FE meter shows the buffer and the bar shows production progress or remaining fuel time. Each machine buffers 16,000 FE. Sided Forge item ports accept inputs and release finished goods. A neighboring port must share the verified Industrial parcel. Processing pauses when output is full or power is unavailable. Removing a machine drops its inventory.

Ownership records store the placing player, town, plot and effective parcel UUIDs. Eligibility follows the current plot, Quarter and physical OPAC claim. A municipal leadership change retains the original record. A new plot or a changed Quarter boundary disables the machine until its original parcel is restored or it is placed again under the new parcel's rules.

## Production and trade

- `/industry info` lists the reports.
- `/industry factories` lists currently loaded, eligible machines in your town.
- `/industry production` shows today's and lifetime town output.
- `/industry nation <name or UUID>` reports active machines and national output.
- `/nation economy industry` opens the production activity report for your current nation.

A reporting day is 24,000 ticks of elapsed Overworld game time. Completed quantities retain the town and nation of manufacture. Affiliation changes affect subsequent completions and current active factory counts. Independent towns retain their own production totals.

Hold a finished product and use Core's `/trade offer <player> <amount> <price> <currency>`. The buyer uses `/trade accept <offer UUID>`. Core performs settlement, applies its current customs and embargo rules, and records any paid trade. Production itself only writes item quantities to the mod's SavedData.

## Datapacks

Default machine and fuel recipes are in `src/main/resources/data/historical_industry/recipes/process/`. Override the same path in a datapack, or add another recipe with type `historical_industry:process`. Recipes accept one or two ordered inputs with vanilla item or tag ingredients, an input count, a machine ID, a tick duration and FE per tick. Processors also require a result stack. Use `/reload` to apply changes.

## Build

1. Build [Historical Nations Core v0.6.0](https://github.com/ZeorezDev/historicalnationscore/tree/2acc008) beside this checkout, or put its release JAR in a directory of your choice.
2. Run `./gradlew build` with Java 17. For another dependency location use `-PcoreLibDir=/path/to/jars`.
3. Find the installable JAR in `build/libs/`. The optional `integrationJar` task builds a separate fixture in `build/integration/` for a disposable dedicated server.

Editable Blockbench projects live in `models/`. Run `node tools/export-models.mjs` to regenerate the Minecraft 1.20.1 geometry, moving parts and texture atlases from the projects.

## Verification

Use an isolated Forge 47.4.28 dedicated server with the three mod JARs and the separate integration fixture. The fixture runs only when the Java system property `industry.test` selects a phase. It logs `INDUSTRY PASS` or `INDUSTRY FAIL` and stops the server. Check the marker even when Java exits successfully.

| Java system property | World | Checks |
| --- | --- | --- |
| `-Dindustry.test=setup` | New world | Two nations, four machines, real FE transfer, produced steel and parts sold through Core, embargo, tariff, balances, claim loss and a saved in-progress batch |
| `-Dindustry.test=verify` | Same world after shutdown | Persistent national totals, inventory and exactly one resumed completion |
| `-Dindustry.test=territory` | Another new world | Quarter boundaries, independent town output, joining and leaving nations, mayoral succession |

The server fixture connects in-process player sessions through Minecraft's player list. The separate client check runs with `./gradlew -PindustryClientTest integrationClasses runClient` and verifies the baked block states, inventory models, animated components and textures in the Forge client.

Licensed under GPL-3.0-only.
