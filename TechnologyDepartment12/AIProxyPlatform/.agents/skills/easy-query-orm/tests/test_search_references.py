import unittest
from pathlib import Path

from scripts.search_references import compile_patterns, iter_matches


class SearchReferencesTests(unittest.TestCase):
    def test_compile_patterns_is_case_insensitive(self) -> None:
        patterns = compile_patterns(["SelectAutoInclude"])
        self.assertIsNotNone(patterns[0].search("selectautoinclude"))

    def test_iter_matches_finds_reference_lines(self) -> None:
        root = Path(__file__).resolve().parents[1]
        matches = iter_matches(root, ["selectAutoInclude"])
        self.assertTrue(
            any("references/select-auto-include.md" in match for match in matches)
        )


if __name__ == "__main__":
    unittest.main()
