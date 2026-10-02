package com.secretvault.cli.command;

import com.secretvault.cli.SecretVaultCli;
import com.secretvault.cli.output.ConsolePrinter;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
        name = "completion",
        description = "Generate shell completion scripts (bash, zsh, fish, powershell)"
)
public class CompletionCommand extends BaseCommand {

    @Parameters(index = "0", description = "Target shell: bash, zsh, fish, powershell")
    private String shell;

    @Override
    public Integer call() {
        ConsolePrinter printer = getPrinter();
        String targetShell = (shell != null) ? shell.toLowerCase().trim() : "bash";

        CommandLine cmdLine = new CommandLine(new SecretVaultCli());

        switch (targetShell) {
            case "bash" -> {
                String script = AutoComplete.bash("secretvault", cmdLine);
                printer.raw(script);
            }
            case "zsh" -> {
                String script = generateZshCompletion();
                printer.raw(script);
            }
            case "fish" -> {
                String script = generateFishCompletion();
                printer.raw(script);
            }
            case "powershell", "ps" -> {
                String script = generatePowerShellCompletion();
                printer.raw(script);
            }
            default -> {
                printer.error("Unsupported shell: '" + shell + "'. Supported: bash, zsh, fish, powershell");
                return 1;
            }
        }
        return 0;
    }

    private String generateZshCompletion() {
        return """
                #compdef secretvault
                _secretvault() {
                    local -a commands
                    commands=(
                        'auth:Manage authentication and credential storage'
                        'workspace:Discover and inspect workspaces'
                        'project:Discover and inspect projects'
                        'environment:Discover and inspect environments'
                        'context:Manage context scopes'
                        'secret:Manage secret lifecycles, reveals, and rollbacks'
                        'env:Pull and push .env files'
                        'run:Execute child process with injected secrets'
                        'dev:Local development workflow helpers'
                        'config:Manage CLI configuration settings'
                        'doctor:Run system diagnostics'
                        'version:Display CLI version'
                        'completion:Generate shell autocompletion'
                    )
                    _describe 'secretvault commands' commands
                }
                compdef _secretvault secretvault
                """;
    }

    private String generateFishCompletion() {
        return """
                # Fish completion for secretvault
                complete -c secretvault -f
                complete -c secretvault -n "__fish_use_subcommand" -a auth -d "Manage authentication"
                complete -c secretvault -n "__fish_use_subcommand" -a workspace -d "Manage workspaces"
                complete -c secretvault -n "__fish_use_subcommand" -a project -d "Manage projects"
                complete -c secretvault -n "__fish_use_subcommand" -a environment -d "Manage environments"
                complete -c secretvault -n "__fish_use_subcommand" -a context -d "Manage context scopes"
                complete -c secretvault -n "__fish_use_subcommand" -a secret -d "Manage secrets"
                complete -c secretvault -n "__fish_use_subcommand" -a env -d "Pull and push .env files"
                complete -c secretvault -n "__fish_use_subcommand" -a run -d "Run process with injected secrets"
                complete -c secretvault -n "__fish_use_subcommand" -a dev -d "Local development helpers"
                complete -c secretvault -n "__fish_use_subcommand" -a config -d "Manage configuration"
                complete -c secretvault -n "__fish_use_subcommand" -a doctor -d "Run diagnostics"
                complete -c secretvault -n "__fish_use_subcommand" -a version -d "Show version"
                """;
    }

    private String generatePowerShellCompletion() {
        return """
                # PowerShell completion for secretvault
                Register-ArgumentCompleter -Native -CommandName 'secretvault' -ScriptBlock {
                    param($wordToComplete, $commandAst, $cursorPosition)
                    $commands = @('auth', 'workspace', 'project', 'environment', 'context', 'secret', 'env', 'run', 'dev', 'config', 'doctor', 'version', 'completion')
                    $commands | Where-Object { $_ -like "$wordToComplete*" } | ForEach-Object {
                        [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_)
                    }
                }
                """;
    }
}
