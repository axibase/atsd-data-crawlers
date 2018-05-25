#!/usr/bin/env bash

script_dir=$(dirname "$0")
cd "$(dirname "$0")"
java -jar ${script_dir}/bin/fred-crawler-exec.jar