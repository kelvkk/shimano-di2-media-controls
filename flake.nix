{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = fn: nixpkgs.lib.genAttrs systems (system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
        in
        fn pkgs
      );
    in
    {
      devShells = forAllSystems (pkgs:
        let
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            buildToolsVersions = [ "34.0.0" "35.0.0" ];
            platformVersions = [ "34" "35" ];
            includeEmulator = false;
            includeNDK = false;
          };
          androidSdk = androidComposition.androidsdk;
        in
        {
          default = pkgs.mkShell {
            buildInputs = with pkgs; [
              gradle
              kotlin
              jdk17
              android-tools
              androidSdk
            ];

            JAVA_HOME = "${pkgs.jdk17}";
            ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          };
        });
    };
}
