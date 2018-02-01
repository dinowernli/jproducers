load("@autotest//bzl:autotest.bzl", "auto_java_test")

auto_java_test(
    name = "tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//src/main/java/me/dinowernli/jproducers",
        "//third_party/guava",
        "//third_party/testing",
    ],
)
