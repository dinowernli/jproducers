# Buildifier
http_archive(
    name = "io_bazel_rules_go",
    sha256 = "d322432e804dfa7936c1f38c24b00f1fb71a0be090a1273d7100a1b4ce281ee7",
    strip_prefix = "rules_go-a390e7f7eac912f6e67dc54acf67aa974d05f9c3",
    urls = [
        "http://bazel-mirror.storage.googleapis.com/github.com/bazelbuild/rules_go/archive/a390e7f7eac912f6e67dc54acf67aa974d05f9c3.tar.gz",
        "https://github.com/bazelbuild/rules_go/archive/a390e7f7eac912f6e67dc54acf67aa974d05f9c3.tar.gz",
    ],
)

load("@io_bazel_rules_go//go:def.bzl", "go_repositories", "go_repository")

go_repositories()

go_repository(
    name = "org_golang_x_tools",
    commit = "3d92dd60033c312e3ae7cac319c792271cf67e37",
    importpath = "golang.org/x/tools",
)

# Autotest
http_archive(
    name = "autotest",
    strip_prefix = "bazel-junit-autotest-0.0.1",
    urls = ["https://github.com/dinowernli/bazel-junit-autotest/archive/v0.0.1.zip"],
)

load("@autotest//bzl:autotest.bzl", "autotest_junit_repo")

autotest_junit_repo(
    autotest_workspace = "@autotest",
    junit_jar = "//third_party/testing",
)

# Direct java deps
maven_jar(
    name = "junit_artifact",
    artifact = "junit:junit:4.10",
)

maven_jar(
    name = "mockito_artifact",
    artifact = "org.mockito:mockito-all:1.10.19",
)

maven_jar(
    name = "truth_artifact",
    artifact = "com.google.truth:truth:0.28",
)

maven_jar(
    name = "com_google_guava",
    artifact = "com.google.guava:guava:23.6-jre",
)

maven_jar(
    name = "com_google_inject_guice",
    artifact = "com.google.inject:guice:4.0",
)

maven_jar(
    name = "javax_inject",
    artifact = "javax.inject:javax.inject:1",
)
