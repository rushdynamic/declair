# declair

A TUI app to find and add packages to your NixOS config

https://github.com/user-attachments/assets/52eb411b-572f-4128-8528-a00f14a32620

## Prerequisites

### Required Dependencies

1. **Babashka** - `nix-env -iA nixpkgs.babashka` (Or follow installation guide at https://babashka.org/)
2. **gum** - `nix-env -iA nixpkgs.gum`

## Installation

1. **Download the script:**

   ```bash
   curl -O https://raw.githubusercontent.com/rushdynamic/declair/refs/heads/main/declair.clj
   chmod +x declair.clj
   ```

2. **Run it using either** `./declair.clj` or `bb declair.clj`

## Usage

### First Run

On your first run, declair will prompt you to configure:

- **NixOS config path**: Path to your `configuration.nix` or `home.nix` file
- **Auto-rebuild**: Whether to automatically run `nixos-rebuild switch` after adding packages

These settings are saved to `~/.config/declair/config.edn`.

### Basic Usage

**Interactive search:**

```bash
declair
```

**Direct search:**

```bash
declair firefox
declair "text editor"
declair python3
```

### Example Workflow

1. **Search for a package:**

   ```bash
   $ declair
   Search for a package: firefox
   ```

2. **Select from results:**

   ```
   Select a package:
   > firefox 121.0 : Mozilla Firefox web browser
     firefox-esr 115.6.0esr : Mozilla Firefox ESR web browser
     firefox-beta 122.0b9 : Mozilla Firefox Beta web browser
   ```

3. **Package gets added to your config:**

   ```nix
   # Before
   environment.systemPackages = with pkgs; [
     vim
     git
   ];

   # After
   environment.systemPackages = with pkgs; [
     vim
     git
     firefox
   ];
   ```

4. **Optional auto-rebuild** (if enabled):
   Automatically runs `sudo nixos-rebuild switch` after adding the package to the config.

## Configuration

Configuration is stored in `~/.config/declair/config.edn`:

```clojure
{:nix-path "/etc/nixos/configuration.nix"
 :auto-rebuild? false}
```

### Configuration Options

- **`:nix-path`** - Path to your NixOS configuration file containing the `environment.systemPackages = with pkgs; []` block
- **`:auto-rebuild?`** - Boolean flag for automatic rebuilding after package addition

### Reconfiguring

To change your configuration, simply delete the config file and run declair again:

```bash
rm ~/.config/declair/config.edn
declair
```

## Supported Config Formats

declair can handle various `with pkgs; [...]` block formats:

**Single line:**

```nix
environment.systemPackages = with pkgs; [ vim git ];
```

**Multi-line with closing bracket on same line:**

```nix
environment.systemPackages = with pkgs; [
  vim
  git ];
```

**Multi-line with standalone closing bracket:**

```nix
environment.systemPackages = with pkgs; [
  vim
  git
];
```

## Troubleshooting

### Common Issues

**"gum is not installed" error:**

```bash
nix-env -iA nixpkgs.gum
```

**"No results found":**

- Try broader search terms
- Check your internet connection
- Ensure nix flakes are enabled

**Permission errors during rebuild:**

- Ensure your user can run `sudo nixos-rebuild switch`
- Check if your NixOS config path is writable

### Debug Mode

For troubleshooting, you can examine the generated backup files:

```bash
diff your-config.nix your-config.nix.declair.bak
```

## Contributing

Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

## License

https://github.com/rushdynamic/declair/blob/main/LICENSE.md

## Acknowledgments

- Built with [Babashka](https://babashka.org/) 🎋
- UI powered by [gum](https://github.com/charmbracelet/gum) 🍬
- Package data from [nixpkgs](https://github.com/NixOS/nixpkgs) ❄️
