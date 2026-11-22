"""Read the child's explicitly shared REM export."""
from pathlib import Path


class SharedREM:
    def __init__(self, directory: Path):
        self.directory = directory.expanduser().resolve(strict=True)
        if not self.directory.is_dir():
            raise ValueError("WHATCHA_SHARED_REM must be a directory")

    def read(self, name: Path) -> str:
        if name.is_absolute() or ".." in name.parts or name.suffix != ".md":
            return ""
        try:
            path = (self.directory / name).resolve(strict=True)
            if not path.is_relative_to(self.directory) or not path.is_file():
                return ""
            with path.open(encoding="utf-8") as note:
                content = note.read(8_000)
        except (OSError, UnicodeError):
            return ""
        return content.partition("## What you typed")[0].strip()

    def recent_sessions(self, count: int = 3) -> str:
        """Read recent work notes explicitly shared with the family, newest first."""
        paths = sorted((self.directory / "sessions").glob("*.md"), reverse=True)
        notes = [self.read(Path("sessions") / path.name) for path in paths[:max(1, min(5, count))]]
        return "\n\n".join(filter(None, notes))[:12_000] or "没有已分享的近期笔记。"

    def show_page(self, page: str) -> str:
        """Read a page in the child's shared REM export, for example projects/lantern.md."""
        return self.read(Path(page)) or "没有这条已分享的笔记。"

    def search_notebook(self, text: str) -> str:
        """Search only the REM notes shared with the family for a short term."""
        term = text.strip().casefold()
        if not 1 <= len(term) <= 120:
            return "请用简短的词搜索。"
        matches = []
        for path in self.directory.rglob("*.md"):
            relative = path.relative_to(self.directory)
            lines = self.read(relative).splitlines()
            match = next((line.strip() for line in lines if term in line.casefold()), None)
            if match:
                matches.append(f"{relative.as_posix()}: {match[:500]}")
            if len(matches) == 8:
                break
        return "\n".join(matches) or "没有找到已分享的笔记。"
