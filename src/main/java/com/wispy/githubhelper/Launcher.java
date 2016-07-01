package com.wispy.githubhelper;

import org.kohsuke.github.*;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class Launcher {
    public static void main(String[] args) throws Exception {
        new Launcher().run();
    }

    private void run() throws Exception {
        String organizationName = getArgument("GIT_ORGANIZATION", "GitHub organization name");
        String repositoryName = getArgument("GIT_REPOSITORY", "GitHub repository name");
        Integer requestNumber = getIntArgument("GIT_PULL_REQUEST_ID", "GitHub pull request id");
        String home = getArgument("LOCAL_REPO_PARENT_DIR", "Parent dir of your local repo, e.g. /home/user/projects)");
        String message = getArgument("GIT_MESSAGE", "GitHub pull request merge message");

        GitHub github = GitHub.connect();
        println("Connected as: " + github.getMyself().getName() + " (" + github.getMyself().getLogin() + ")");

        GHOrganization organization = getOrganization(github, organizationName);
        GHRepository repository = getRepository(organization, repositoryName);
        String targetBranch = repository.getDefaultBranch(); // the branch we want to merge pull request into, in most cases this is 'master'

        GHPullRequest request = getPullRequest(repository, requestNumber);
        requestNumber = request.getNumber();
        String requestSource = request.getHead().getRepository().getHtmlUrl().toString();
        String requestRef = request.getHead().getRef();
        GitUser requestUser = request.getHead().getCommit().getCommitShortInfo().getCommitter();
        String requestAuthor = requestUser.getName() + " <" + requestUser.getEmail() + ">";
        println("Pull request source: " + requestSource + " " + requestRef);
        println("Pull request author: " + requestAuthor);
        if (!request.getMergeable()) {
            throw new IllegalStateException("Pull request is not mergeable");
        }

        home = input(home, "Parent dir of your local repo, e.g. /home/user/projects");
        File local = new File(home + "/" + repository.getName());
        if (!local.isDirectory()) {
            throw new IllegalStateException("Specified local repository does not exist: " + local.getAbsolutePath());
        }

        message = input(message, "merge commit message (skip for '" + request.getTitle() + "')");
        if (message.isEmpty()) {
            message = request.getTitle();
        }

        Set<String> remoteRepoNames = getRemoteRepoNames(local);
        String targetRemoteRepo = remoteRepoNames.contains("upstream") ? "upstream" : "origin";
        println("Target remote repo to push: " + targetRemoteRepo);

        execute(local, "git status");
        execute(local, "git pull --all");

        execute(local, "git checkout -b merge-pull-request " + targetBranch);
        execute(local, "git pull " + requestSource + " " + requestRef);

        execute(local, "git checkout " + targetBranch);
        checkNoLocalChanges(local, targetBranch);

        execute(local, "git merge merge-pull-request");
        execute(local, "git reset " + targetRemoteRepo + "/" + targetBranch);
        execute(local, "git add .");
        execute(local, String.format("git commit -a -m \"%s\" -m \"Closes #%s\" --author \"%s\"", message, requestNumber, requestAuthor));

        execute(local, "git push " + targetRemoteRepo + " " + targetBranch);
        execute(local, "git branch -D merge-pull-request");

        request.close();
    }

    private GHOrganization getOrganization(GitHub github, String organizationName) throws Exception {
        GHOrganization organization;
        if (organizationName == null || (organization = github.getOrganization(organizationName)) == null) {
            if (organizationName != null) {
                println("Organization '" + organizationName + "' not found");
            }
            organization = select("organization", github.getMyOrganizations().values(), GHOrganization::getLogin);
        }
        return organization;
    }

    private GHRepository getRepository(GHOrganization organization, String repositoryName) throws Exception {
        GHRepository repository;
        if (repositoryName == null || (repository = organization.getRepository(repositoryName)) == null) {
            if (repositoryName != null) {
                println("Repository '" + repositoryName + "' not found");
            }
            repository = select("repository", organization.getRepositories().values(), GHRepository::getName);
        }
        return repository;
    }

    private GHPullRequest getPullRequest(GHRepository repository, Integer requestId) throws Exception {
        GHPullRequest request;
        if (requestId == null || (request = repository.getPullRequest(requestId)) == null) {
            if (requestId != null) {
                println("Request with id '" + requestId + "' not found");
            }
            request = select("pull request", repository.getPullRequests(GHIssueState.OPEN), r -> "#" + r.getNumber() + " " + r.getTitle());
        }
        if (request.getState() != GHIssueState.OPEN) {
            throw new IllegalStateException("Pull request '" + request.getTitle() + "' is already closed");
        }
        return request;
    }

    private String getArgument(String key, String description) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            println(description + " is not set (" + key + ")");
            return null;
        }
        println(description + " = " + value);
        return value;
    }

    private Integer getIntArgument(String key, String description) {
        String value = getArgument(key, description);
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private String input(String value, String description) {
        if (value != null) {
            return value;
        }
        println("--------------------------");
        println("Enter " + description + ":");
        print("> ");
        Scanner scanner = new Scanner(System.in);
        return scanner.nextLine();
    }

    private <T> T select(String label, Collection<T> values, CheckedFunction<T, String> nameSupplier) throws Exception {
        T[] array = (T[]) values.toArray();
        if (array.length == 0) {
            throw new IllegalStateException("No " + label + "s found");
        }

        println("--------------------------");
        println("Select " + label + ":");
        for (int index = 0, length = array.length; index < length; index++) {
            println(" [" + index + "] " + nameSupplier.apply(array[index]));
        }

        print("> ");
        Scanner scanner = new Scanner(System.in);
        int value = scanner.nextInt();
        return array[value];
    }

    private void println(String string) {
        System.out.println(string);
    }

    private void printError(String string) {
        System.err.println(string);
    }

    private void print(String string) {
        System.out.print(string);
    }

    private List<String> execute(File workDirectory, String command) throws Exception {
        println("--------------------------");
        println("Running: " + command);
        println("Directory: " + workDirectory.getAbsolutePath());
        Process process = new ProcessBuilder("/bin/bash").directory(workDirectory).start();
        PrintWriter input = new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream())), true);
        input.println(command);
        input.println("exit");

        int exitCode = process.waitFor();
        BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        List<String> outputLines = new ArrayList<>();
        while ((line = output.readLine()) != null) {
            println(" - " + line);
            outputLines.add(line);
        }

        BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = error.readLine()) != null) {
            printError(" - " + line);
        }
        if (exitCode != 0) {
            throw new RuntimeException("Command '" + command + "' exited with code: " + exitCode);
        }

        return outputLines;
    }

    private Set<String> getRemoteRepoNames(File workDir) throws Exception {
        List<String> output = execute(workDir, "git remote -v");
        return output.stream().map(line -> line.split("\\s")[0]).collect(Collectors.toSet());
    }

    private void checkNoLocalChanges(File workDirectory, String targetBranch) throws Exception {
        List<String> output = execute(workDirectory, "git status");
        // it prints 'working directory clean' on two lines, if more - there are untracked on changes to be commited
        if (output.size() > 2) {
            printError("");
            printError("There are local changes in branch " + targetBranch + ". It's not safe to proceed. Exiting.");
            System.exit(1);
        }
    }
}
