package(default_visibility = ["//visibility:public"])

java_binary(
    name = "main",
    main_class = "me.dinowernli.jproducers.Main",
    runtime_deps = [
        ":jproducers-lib",
    ],
)

java_library(
    name = "jproducers-lib",
    srcs = glob(["*.java"]),
    deps = [
        "//third_party/guava",
    ],
)
