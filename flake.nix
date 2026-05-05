{
  description = "playq-tk dev shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; config.allowUnfree = true; };
        jdk = pkgs.graalvmPackages.graalvm-ce;
        sbt = pkgs.sbt.override { jre = jdk; };
      in {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            sbt
            pkgs.scala-cli
            pkgs.coursier
          ];

          JAVA_HOME = "${jdk}/lib/openjdk";
        };
      });
}
