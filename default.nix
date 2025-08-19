{ lib, stdenvNoCC, fetchFromGitHub, babashka, gum, jq }:

stdenvNoCC.mkDerivation rec {
  pname = "declair";
  version = "0.1.0";

  src = fetchFromGitHub {
    owner = "rushdynamic";
    repo = "declair";
    rev = "v${version}";
    sha256 = "sha256-V9GATRWjhGHeZzWDkjqxtZ80nGgEvsNxZnoNC5xev00=";
  };

  buildInputs = [ babashka gum jq ];

  installPhase = ''
    mkdir -p $out/bin
    cp declair.clj $out/bin/declair
    chmod +x $out/bin/declair
  '';

  meta = with lib; {
    description = "A TUI app to search and add packages declaratively to your NixOS config";
    mainProgram = "declair";
    homepage = "https://github.com/rushdynamic/declair";
    license = licenses.mit;
    maintainers = with maintainers; [ rushdynamic ];
    platforms = platforms.linux;
  };
}
