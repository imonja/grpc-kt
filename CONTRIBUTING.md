# Contributing to grpc-kt

Thank you for considering contributing to **grpc-kt**!  
This guide will help you get started and make your contribution process smooth and productive.

---

## 🚀 Contribution Workflow

This project uses the standard fork-based GitHub workflow.

You do **not** need write access to this repository to contribute.  
For most contributions, please create a fork, make changes in your fork, and open a Pull Request.

### 1. Fork the Repository

Click **Fork** on GitHub to create your own copy of this repository.

### 2. Clone Your Fork

```bash
git clone https://github.com/YOUR_USERNAME/grpc-kt.git
cd grpc-kt
```

### 3. Create a Branch

Create a new branch in your fork:

```bash
git checkout -b fix/my-change
```

Please do not work directly on the `main` branch.

Good branch name examples:

```text
fix/proto-type-mapping
feat/client-streaming
docs/update-readme
test/generator-edge-cases
```

### 4. Make Your Changes

Make your changes locally, then commit them using a clear commit message.

If possible, follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) format:

```bash
git commit -m "fix: correct proto type mapping"
```

Common commit prefixes:

```text
fix:      bug fix
feat:     new feature
docs:     documentation changes
test:     tests only
refactor: code cleanup without behavior changes
chore:    build or maintenance changes
```

### 5. Push Your Branch

Push your branch to your fork:

```bash
git push origin fix/my-change
```

### 6. Open a Pull Request

Open a Pull Request from your fork to:

```text
imonja/grpc-kt:main
```

Please keep Pull Requests small and focused.  
Large unrelated changes are harder to review and may take longer to merge.

---

## 🛠️ Set Up Environment

Before making changes, make sure your local environment is ready.

Required tools:

- Java 17+; JDK 21 is recommended
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

Kotlin formatting is enforced via **ktlint**.

Run this before committing:

```bash
./gradlew ktlintCheck
```

If formatting issues are found, fix them before opening a Pull Request.

---

## 🧪 Running Tests

Make sure all tests pass before creating a Pull Request:

```bash
./gradlew test
```

For larger changes, also run the full build:

```bash
./gradlew build
```

---

## 🧠 Making Changes

- All contributions should target the `main` branch.
- Prefer small, focused Pull Requests over large changesets.
- Document public APIs and add examples where appropriate.
- Update `README.md` if your change affects usage, setup, or public behavior.
- Add or update tests when changing generator behavior or runtime behavior.
- Avoid unrelated formatting-only changes in files you are not modifying.

---

## ✅ Checklist Before Submitting a PR

Before opening a Pull Request, please make sure:

- [ ] Code builds successfully via `./gradlew build`
- [ ] Tests pass via `./gradlew test`
- [ ] Code style passes via `./gradlew ktlintCheck`
- [ ] New features are documented or include comments where needed
- [ ] `README.md` is updated if needed
- [ ] The Pull Request targets the `main` branch
- [ ] The Pull Request describes what changed and why

---

## 🔐 License

By contributing to this repository, you agree that your contributions will be licensed under the same [Apache License 2.0](LICENSE) as the project itself.

---

## 💬 Questions or Issues?

Feel free to open an [Issue](https://github.com/imonja/grpc-kt/issues) for bug reports, suggestions, or general discussions.

Happy hacking! 🚀
