<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHSell

*/sell allpages et rachat des ventes récentes pour ShopGUIPlus.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

- **`/sell allpages`** : vend l'inventaire porté puis chacune des autres pages d'inventaire de SHInventoryPages, exactement comme `/sell all` (mêmes prix et multiplicateurs, événements de transaction, `shop.log`, objets gratuits exclus, mondes et modes de jeu interdits respectés). Chaque page est posée le temps d'un tick comme inventaire porté puis remise à sa place ; une copie de toutes les pages est gardée dans l'historique de SHInventoryPages juste avant. Un seul message résume la vente. La sous-commande se branche devant l'exécuteur de `/sell` de ShopGUIPlus : alias de `commands.yml` compris, et `allpages` est proposé à la complétion.
- **Rachat** (`/rachat`) : chaque objet vendu au shop (`/sell hand`, `/sell all`, `/sell allpages`, clic dans la boutique) reste rachetable pendant `buyback.duration-minutes` au prix qu'il a rapporté. Ce sont les objets exacts qui sont gardés (nom, enchantements, usure), relevés dans l'inventaire avant et après chaque vente. Une vente de masse ne remplit pas le menu : les ventes du même objet s'additionnent dans une seule entrée, le butin de ferme listé dans `buyback.ignored` n'est pas gardé, et au-delà de `buyback.max-entries` entrées la moins chère part. Clic : une pile ; shift + clic : tout. Les objets rachetés vont dans l'inventaire porté puis dans les autres pages.

Chaque rachat est écrit dans le fichier `rachats` de SHLogs (`plugins/SHLogs/SHSell/`).

Utilisé sur : **Opale**.

## Dépendances

- Obligatoire : `ShopGUIPlus` (1.113)
- Optionnelles : `SHInventoryPages` (pages d'inventaire), `SHLogs` (journal des rachats, sinon console)

`/sell allpages` passe par `ShopManager#handleItemSellAll` et les réglages `Settings` de ShopGUIPlus, qui ne font pas partie de son API publiée : ils sont résolus au démarrage, et seule cette commande est coupée (message dans la console) si une mise à jour de ShopGUIPlus les a changés. Les ventes de l'addon SellGUI ne sont pas relevées pour le rachat (il ne signale ses ventes qu'après coup).

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/sell allpages` | | Vend l'inventaire porté et toutes les pages d'inventaire. | `shsell.allpages` et `shopguiplus.sell.all` |
| `/rachat` | `/buyback` | Menu de rachat des ventes récentes. | `shsell.buyback` |
| `/rachat <joueur>` | `/buyback` | Menu de rachat d'un joueur, en lecture seule. | `shsell.admin.view` |
| `/shsell reload` | | Recharge `config.yml` et `messages.yml` et relance la sauvegarde de `rachats.yml` avec le nouvel intervalle. Un fichier illisible annule le rechargement : l'ancienne configuration reste active et le fichier fautif est indiqué. | `shsell.admin.reload` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shsell.allpages` | /sell allpages (demande aussi shopguiplus.sell.all). | `true` |
| `shsell.buyback` | Racheter ses propres ventes récentes. | `true` |
| `shsell.admin` | Toute l'administration (parent de `shsell.admin.reload` et `shsell.admin.view`). | `op` |
| `shsell.admin.reload` | Recharger `config.yml` et `messages.yml` (`/shsell reload`). | `op` |
| `shsell.admin.view` | Consulter le menu de rachat d'un autre joueur (`/rachat <joueur>`). | `op` |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHSell/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

Les ventes rachetables sont gardées dans `plugins/SHSell/rachats.yml`.

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
