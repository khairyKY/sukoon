# Third-party notices

Sukoon's FreeStyle Libre 2 support (`app/src/main/java/com/sukoon/app/data/source/libre/Libre2.kt`,
`FactoryCalibrationTables.kt`, and the NFC/BLE flow in `LibreNfc.kt` / `LibreBleSource.kt`) is a
Kotlin port of code from **GlucoseDirect**, whose Libre 2 implementation in turn credits **DiaBLE**
and **LibreTools**. All three are MIT-licensed; their notices follow.

`tools/libre2_reference.py` is an independent Python transliteration of the same GlucoseDirect
code, used only to generate test vectors.

---

## GlucoseDirect — https://github.com/creepymonster/GlucoseDirectApp

MIT License

Copyright (c) 2023 Reimar Metzen

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## DiaBLE — https://github.com/gui-dos/DiaBLE

MIT License — Copyright (c) 2026 Guido Soranzio. (Same permission notice and warranty disclaimer as above.)

## LibreTools — https://github.com/ivalkou/LibreTools

MIT License — Copyright (c) 2020 Ivan Valkou. (Same permission notice and warranty disclaimer as above.)
