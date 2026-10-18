"""Compare root-readable snapshots of generated fixtures on the isolated emulator."""
import argparse
import hashlib
import json
from pathlib import Path
import sqlite3
import xml.etree.ElementTree as ET


def snapshot(directory):
    directory = Path(directory)
    with sqlite3.connect(directory / "databases/library.db") as db:
        books = db.execute("SELECT id,title,charset,length,source_bytes,offset,locator_version FROM books ORDER BY id").fetchall()
        bookmarks = db.execute("SELECT book_id,offset,label FROM bookmarks ORDER BY book_id,offset").fetchall()
    originals = {p.parent.name: hashlib.sha256(p.read_bytes()).hexdigest()
                 for p in (directory / "files/books").glob("*/original.bin")}
    prefs = {}
    for node in ET.parse(directory / "shared_prefs/reader.xml").getroot():
        prefs[node.attrib["name"]] = node.attrib.get("value", node.text)
    return dict(books=books, bookmarks=bookmarks, originals=originals, prefs=prefs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before")
    parser.add_argument("after")
    parser.add_argument("--reader-changed", action="store_true")
    args = parser.parse_args()
    before, after = snapshot(args.before), snapshot(args.after)
    assert len(before["books"]) == 2 and before["bookmarks"], "M1 fixtures were not prepared"
    for field in ("books", "bookmarks", "originals"):
        assert before[field] == after[field], f"Upgrade changed {field}"
    assert all(key == value for key, value in after["originals"].items())
    for key in ("font", "brightness", "info", "lastBook", "reopen"):
        assert before["prefs"][key] == after["prefs"][key], f"Upgrade changed {key}"
    legacy = int(before["prefs"]["theme"])
    assert after["prefs"]["ui_theme"] == str(legacy)
    assert after["prefs"]["reader_theme"] == str((legacy + 1) % 3 if args.reader_changed else legacy)
    assert after["prefs"]["shelf_style"] == "1"
    assert after["prefs"]["appearance_version"] == "1"
    print(json.dumps({"result": "pass", "books": len(after["books"]), "bookmarks": len(after["bookmarks"]),
                      "originals": after["originals"], "preferences": after["prefs"],
                      "migrationRepeated": False}, ensure_ascii=False, indent=2))
