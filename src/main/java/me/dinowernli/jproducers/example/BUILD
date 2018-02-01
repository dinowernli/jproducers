package(default_visibility = ["//visibility:public"])

java_binary(
    name = "example",
    srcs = glob(["*.java"]),
    main_class = "me.dinowernli.jproducers.example.Main",
    deps = [
        "//src/main/java/me/dinowernli/jproducers",
        "//third_party/guava",
        "//third_party/guice",
    ],
)
