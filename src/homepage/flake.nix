{
  description = "james homepage build env";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
  };

  outputs =
    { self, nixpkgs }@inputs:
    let
      forAllSystems = nixpkgs.lib.genAttrs nixpkgs.lib.platforms.all;
    in
    {
      devShell = forAllSystems (
        system:
        let
          pkgs = import nixpkgs { inherit system; };
          env = pkgs.bundlerEnv {
            name = "james homepagebuild env";
            gemdir = ./.;
            groups = [
              "default"
              "development"
              "test"
            ];

            meta = with pkgs.lib; {
              description = "james homepagebuild env";
              platforms = platforms.unix;
            };
          };
        in
        pkgs.mkShell {
          buildInputs = [
            env
            env.wrappedRuby
            pkgs.bundix
          ];
        }
      );
    };
}