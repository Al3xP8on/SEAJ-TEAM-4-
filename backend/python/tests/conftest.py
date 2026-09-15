import sys
from pathlib import Path

# Add the parent directory (backend/python) to sys.path so relative imports work - for pytests
sys.path.insert(0, str(Path(__file__).parent.parent))
