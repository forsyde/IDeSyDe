MINIZINC_DIR := minizinc
PYTHON_MINIZINC_DIR := python/desyder/minizinc

all:\
	python-minizinc.task

# getting all python files
python-minizinc.task:
	@mkdir -p $(PYTHON_MINIZINC_DIR)
	@touch $(PYTHON_MINIZINC_DIR)/__init__.py
	@cp -r $(MINIZINC_DIR)/*.mzn $(PYTHON_MINIZINC_DIR)
