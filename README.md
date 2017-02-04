Android Chunk format reader/writer
==================================

This project contains classes extracted from the
[android-arscblamer](https://github.com/google/android-arscblamer) project
that deal with reading and writing the resource table and compiled XML files
present in APK files.

The following modifications have been made:

* Deleted `ArscBlamer`, `ArscDumper`, `ArscModule`, `ResourceEntryStatsCollector`,
  `InjectedApplication`, `BindingAnnotations` and `CommonParams`
* The package has been renamed to `pink.madis.apk.arsc` to avoid collisions

License
-------

Original work Copyright 2016 Google Inc.
Modified work Copyright 2017 Madis Pink

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
