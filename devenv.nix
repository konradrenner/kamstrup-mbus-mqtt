{ pkgs, lib, config, inputs, ... }:

{
  # https://devenv.sh/basics/
  env.GREET = "devenv";

  # https://devenv.sh/packages/
  packages = [
    pkgs.git
    pkgs.graphviz
    pkgs.fontconfig
    pkgs.plantuml
    pkgs.vscode
    pkgs.vscode-extensions.redhat.java
    pkgs.vscode-extensions.jebbs.plantuml
    pkgs.vscode-extensions.asciidoctor.asciidoctor-vscode
    pkgs.quarkus
    pkgs.gh
    pkgs.curl
    pkgs.jq
    pkgs.mosquitto
    ];

  # https://devenv.sh/languages/
  languages.java = {
    enable = true;
    jdk.package = pkgs.graalvmPackages.graalvm-ce;
    maven.enable = true;
  };

  # https://devenv.sh/processes/
  # processes.dev.exec = "${lib.getExe pkgs.watchexec} -n -- ls -la";
  processes.mosquitto.exec = "${lib.getExe pkgs.mosquitto} -p 1883 -v";

  # https://devenv.sh/services/
  # services.postgres.enable = true;

  # https://devenv.sh/scripts/
  scripts.hello.exec = ''
    echo hello from $GREET
  '';

  # https://devenv.sh/basics/
  enterShell = ''
    hello         # Run scripts directly
    git --version # Use packages

    OS_TYPE=$(uname)
    if [[ "$OS_TYPE" == "Linux" ]]; then

      if [ -f /etc/profile ]; then
        source /etc/profile
      fi
      if [ -f ~/.bashrc ]; then
        source ~/.bashrc
      fi

      alias code="code --no-sandbox ."

    elif [[ "$OS_TYPE" == "Darwin" ]]; then
      alias code="code --no-sandbox ."

    else
      echo "Nicht unterstütztes Betriebssystem: $OS_TYPE"
      exit 1
    fi
  '';

  # https://devenv.sh/tasks/
  # tasks = {
  #   "myproj:setup".exec = "mytool build";
  #   "devenv:enterShell".after = [ "myproj:setup" ];
  # };

  # https://devenv.sh/tests/
  enterTest = ''
    echo "Running tests"
    git --version | grep --color=auto "${pkgs.git.version}"

  '';

  # https://devenv.sh/git-hooks/
  # git-hooks.hooks.shellcheck.enable = true;

  # See full reference at https://devenv.sh/reference/options/
}
