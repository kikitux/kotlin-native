#!/usr/bin/env bash

DIR=$(dirname "$0")
PATH=../../dist/bin:../../bin:$PATH

TF_TARGET_DIRECTORY="$HOME/.konan/third-party/tensorflow"
TF_TYPE="cpu" # Change to "gpu" for GPU support

if [ x$TARGET == x ]; then
case "$OSTYPE" in
  darwin*)  TARGET=macbook; TF_TARGET=darwin ;;
  linux*)   TARGET=linux; TF_TARGET=linux ;;
  *)        echo "unknown: $OSTYPE" && exit 1;;
esac
fi

if [ ! -d $TF_TARGET_DIRECTORY/include/tensorflow ]; then
 echo "Installing TensorFlow into $TF_TARGET_DIRECTORY ..."
 mkdir -p $TF_TARGET_DIRECTORY
 curl -s -L \
   "https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-${TF_TYPE}-${TF_TARGET}-x86_64-1.1.0.tar.gz" |
   tar -C $TF_TARGET_DIRECTORY -xz
fi