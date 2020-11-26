MINIZINC_DIR := minizinc
PYTHON_MINIZINC_DIR := python/desyder/minizinc

all:\
	python-minizinc

# getting all python files
python-minizinc:
	@mkdir -p $(PYTHON_MINIZINC_DIR)
	@touch $(PYTHON_MINIZINC_DIR)/__init__.py
	@cp -rf $(MINIZINC_DIR)/*.mzn $(PYTHON_MINIZINC_DIR)
