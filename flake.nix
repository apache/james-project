{
  description = "James dev env";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";

  outputs = { self, nixpkgs }:
    let 
      javaVersion = 21; # Change this value to update the whole stack

      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSupportedSystem = f: nixpkgs.lib.genAttrs supportedSystems (system: f {
        pkgs = import nixpkgs { inherit system; overlays = [ self.overlays.default ]; };
      });
    in
    {
      overlays.default =
        final: prev:
        let
          jdk = prev."temurin-bin-${toString javaVersion}";
        in
        {
          inherit jdk;
          maven = prev.maven.override { jdk_headless = jdk; };
          # gradle = prev.gradle.override { java = jdk; };
        };

      devShells = forEachSupportedSystem ({ pkgs }: {
        default = pkgs.mkShell {
          packages = with pkgs; [
            antora  # build documentation
            cacert  # ssl certificate management
            dive    # explore generated docker images
            git     # version control
            jdk     # build and run james
            jekyll  # homepage and blog
            maven   # build james
          ];
          MAVEN_OPTS = "-Djna.library.path="  + pkgs.lib.makeLibraryPath [pkgs.udev];
          JAVA_HOME = "${pkgs.jdk}";
          shellHook =
            let
              prev = "\${JAVA_TOOL_OPTIONS:+ $JAVA_TOOL_OPTIONS}";
            in
            ''
              export JAVA_TOOL_OPTIONS="${prev}"
              echo "Experimental JAMES development environment activated"
            '';
        };
      });
    };
}
