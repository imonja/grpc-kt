# Contributing to grpc-kt

Thank you for considering contributing to **grpc-kt**!  
This guide will help you get started and make your contribution process smooth and productive.

---

## 🚀 Getting Started

### 1. Fork & Clone

```bash
git clone https://github.com/YOUR_USERNAME/grpc-kt.git
cd grpc-kt
```

### 2. Set Up Environment

- Java 17+ (JDK 21 recommended)
- Kotlin 2.1.0
- Gradle 8.0+
- Protoc 4.30.x

Install dependencies and check the build:

```bash
./gradlew build
```

---

## 📦 Project Structure

- `generator/` — Kotlin protoc plugin implementation
- `common/` — runtime support for generated code
- `example/` — usage examples and proto files

---

## 📄 Code Style

- Kotlin formatting is enforced via **ktlint**.
- Run this before committing:

```bash
./gradlew ktlintCheck
```

---

## 🧪 Running Tests

Make sure all tests pass before creating a pull request:

```bash
./gradlew test
```

---

## 🧠 Making Changes

- All contributions should target the `main` branch.
- Follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) format if possible (e.g. `fix: correct proto type mapping`).
- Document public APIs and add examples where appropriate.
- Prefer small, focused pull requests over large changesets.

---

## ✅ Checklist Before Submitting a PR

- [ ] Code builds successfully via `./gradlew build`
- [ ] Tests pass: `./gradlew test`
- [ ] Code is formatted: `./gradlew ktlintCheck`
- [ ] New features are documented or include comments
- [ ] `README.md` is updated if needed

---

## 🔐 License

By contributing to this repository, you agree that your contributions will be licensed under the same [Apache License 2.0](LICENSE) as the project itself.

---

## 💬 Questions or Issues?

Feel free to open an [Issue](https://github.com/imonja/grpc-kt/issues) for bug reports, suggestions, or general discussions.

Happy hacking! 🚀
