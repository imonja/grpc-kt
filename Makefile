last-tag:
	@git fetch --tags > /dev/null; \
	latest=$$(git tag --sort=-creatordate | head -n 1); \
	echo "🔖 Latest tag: $$latest"

add-tag-and-push:
	@read -p "Enter tag name (e.g. 1.1.1): " tag; \
	read -p "Enter tag message: " msg; \
	if [ -z "$$tag" ] || [ -z "$$msg" ]; then \
		echo "❌ Tag name and message are required."; \
		exit 1; \
	fi; \
	echo "🔖 Creating tag '$$tag' with message: $$msg"; \
	git tag -a "$$tag" -m "$$msg"; \
	git push origin "$$tag"; \
	echo "✅ Tag $$tag pushed successfully."

