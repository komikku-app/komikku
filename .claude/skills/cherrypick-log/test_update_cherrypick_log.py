"""Unit tests for update_cherrypick_log.py (stdlib only, no git repository needed).

Run: python3 -m unittest discover -s .claude/skills/cherrypick-log -p 'test_*.py'
"""

import sys
import unittest
from pathlib import Path
from unittest import mock

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

import update_cherrypick_log as m  # noqa: E402

UP = m.Upstream("main", "main", "main", "owner/repo")


def commit(ch: str, message: str, date: str) -> m.Commit:
    return m.Commit(ch * 40, date, "a", "a@x", "a", "a@x", message, repo=UP.name)


def revert_of(ch: str, target: m.Commit, date: str) -> m.Commit:
    return commit(ch, f'Revert "{target.subject}"\n\nThis reverts commit {target.full}.', date)


def render(commits, states=None):
    """Rows as cmd_update builds them: (entry, cells) with status and notes from `states`."""
    states = states or [("", "")] * len(commits)
    return [
        (m.Entry(c, UP, "primary", c.full), [st, "", UP.name, c.date, c.subject, "a", "", notes])
        for c, (st, notes) in zip(commits, states)
    ]


def rerender(rows, extra=()):
    """Next run: release script omissions like cmd_update does, then add new commits."""
    released = [(e, list(m.release_script_flag(c[0], c[-1]))) for e, c in rows]
    return render([e.commit for e, _ in released] + list(extra), [tuple(x) for _, x in released] + [("", "")] * len(extra))


def statuses(rows):
    return [cells[0] for _, cells in rows]


def fake_builder(cfg, logs, changed=None):
    """A Builder reading `logs` (newest first, like git log) and `changed` paths instead of git."""

    class FakeBuilder(m.Builder):
        def log(self, up, since):
            return [x for x in logs[up.name] if not since or x.date >= since]

        def path_excluded(self, up):
            text = "".join(f"\x1e{c.full}\n\n" + "\n".join((changed or {}).get(c.full, [])) for c in logs[up.name])
            with mock.patch.object(m, "git", return_value=text):
                return super().path_excluded(up)

    return FakeBuilder(Path("."), cfg)


def mk(ch, msg, date, repo):
    return m.Commit(ch * 40, date, "a", "a@x", "a", "a@x", msg, repo=repo)


class RevertChainTest(unittest.TestCase):
    def setUp(self):
        self.c = commit("c", "Feature", "2026-01-01")
        self.r1 = revert_of("d", self.c, "2026-01-02")
        self.r2 = revert_of("e", self.r1, "2026-01-03")

    def test_pair_is_flagged_with_previous_status(self):
        rows = render([self.c, self.r1], [("?", ""), ("", "")])
        m.flag_revert_chains(rows)
        self.assertEqual(statuses(rows), ["?", "?"])
        self.assertIn("Cancels out (script): reverted by", rows[0][1][-1])
        self.assertIn("[was: ?]", rows[0][1][-1])
        self.assertIn("[was: empty]", rows[1][1][-1])

    def test_extended_chain_is_recomputed(self):
        rows = render([self.c, self.r1])
        m.flag_revert_chains(rows)
        rows = rerender(rows, extra=[self.r2])
        m.flag_revert_chains(rows)
        # odd chain: the original keeps the net change, the revert pair cancels out
        self.assertEqual(statuses(rows), ["", "?", "?"])
        self.assertEqual(rows[0][1][-1], "")

    def test_rerun_is_stable(self):
        rows = render([self.c, self.r1])
        m.flag_revert_chains(rows)
        first = [list(c) for _, c in rows]
        rows = rerender(rows)
        m.flag_revert_chains(rows)
        self.assertEqual([c for _, c in rows], first)

    def test_legacy_x_omission_becomes_flag(self):
        legacy = "Omitted (script): cancels out - reverted by `dddddddddd` [was: empty]"
        rows = render([self.c, self.r1], [("X", legacy), ("X", legacy.replace("reverted by", "reverts"))])
        rows = rerender(rows)
        m.flag_revert_chains(rows)
        self.assertEqual(statuses(rows), ["?", "?"])
        self.assertNotIn("Omitted (script)", rows[0][1][-1])

    def test_manual_x_blocks_chain(self):
        rows = render([self.c, self.r1], [("X", "won't pick"), ("", "")])
        m.flag_revert_chains(rows)
        self.assertEqual(statuses(rows), ["X", ""])
        self.assertEqual(rows[0][1][-1], "won't pick")

    def test_ai_verdict_blocks_chain(self):
        rows = render([self.c, self.r1], [("", "AI checked 2026-10-03: not found - x"), ("", "")])
        m.flag_revert_chains(rows)
        self.assertEqual(statuses(rows), ["", ""])

    def test_partial_pick_blocks_chain(self):
        note = "Partial 2026-11-02: picked without x"
        rows = render([self.c, self.r1], [("?", note), ("", "")])
        m.flag_revert_chains(rows)
        self.assertEqual(statuses(rows), ["?", ""])
        self.assertEqual(rows[0][1][-1], note)

    def test_picked_side_blocks_chain(self):
        rows = render([self.c, self.r1], [("O", ""), ("", "")])
        m.flag_revert_chains(rows)
        self.assertEqual(statuses(rows), ["O", ""])


class ReleaseTest(unittest.TestCase):
    def test_manual_x_untouched(self):
        self.assertEqual(m.release_script_flag("X", "my reason"), ("X", "my reason"))

    def test_restores_recorded_status_and_keeps_user_notes(self):
        notes = "mine; Cancels out (script): reverts `abc` [was: ?]"
        self.assertEqual(m.release_script_flag("?", notes), ("?", "mine"))
        self.assertEqual(m.release_script_flag("?", "Cancels out (script): reverts `abc` [was: empty]"), ("", ""))

    def test_legacy_x_note_is_released(self):
        self.assertEqual(m.release_script_flag("X", "Omitted (script): cancels out - reverts `abc`"), ("", ""))
        self.assertEqual(m.release_script_flag("X", "Omitted (script): x [was: ?]"), ("?", ""))

    def test_manual_x_on_new_flag_is_kept(self):
        self.assertEqual(m.release_script_flag("X", "Cancels out (script): reverts `abc` [was: empty]"), ("X", ""))

    def test_ai_reviewed_row_untouched(self):
        notes = "Cancels out (script): reverts `abc` [was: empty]; AI checked 2026-10-06: not found - x"
        self.assertEqual(m.release_script_flag("?", notes), ("?", notes))

    def test_user_text_before_mark_is_kept(self):
        notes = "ok, Cancels out (script): reverts `abc` [was: empty];mine"
        self.assertEqual(m.release_script_flag("?", notes), ("", "ok;mine"))
        self.assertEqual(m.release_script_flag("?", "a &lt; Cancels out (script): x [was: empty]"), ("", "a &lt;"))

    def test_bogus_recorded_status_is_ignored(self):
        self.assertEqual(m.release_script_flag("?", "Cancels out (script): x [was: done]"), ("", ""))

    def test_stale_note_removed_when_status_changed_by_hand(self):
        notes = "Cancels out (script): reverts `abc` [was: empty]; picked manually"
        self.assertEqual(m.release_script_flag("O", notes), ("O", "picked manually"))

    def test_escaped_entity_in_note_is_kept(self):
        notes = "use a &lt;b&gt; tag; Cancels out (script): x [was: empty]"
        self.assertEqual(m.release_script_flag("?", notes), ("", "use a &lt;b&gt; tag"))

    def test_user_separators_are_kept(self):
        notes = "a;b; Cancels out (script): x [was: empty]; c"
        self.assertEqual(m.release_script_flag("?", notes), ("", "a;b; c"))


class CarryOverTest(unittest.TestCase):
    def test_manual_status_kept(self):
        self.assertEqual(m.carry_over("O", "", ""), ("O", ""))
        self.assertEqual(m.carry_over("X", "no", "O"), ("X", "no"))

    def test_rechecked_without_ai_note(self):
        self.assertEqual(m.carry_over("?", "", ""), ("", ""))
        self.assertEqual(m.carry_over("", "", "?"), ("?", ""))

    def test_ai_verdict_kept_unless_strong_evidence(self):
        note = "AI checked 2026-10-03: partial - x"
        self.assertEqual(m.carry_over("?", note, ""), ("?", note))
        self.assertEqual(m.carry_over("", note, "?"), ("", note))
        self.assertEqual(m.carry_over("?", note, "O"), ("O", note))

    def test_partial_pick_kept_unless_strong_evidence(self):
        for note in (
            "Partial 2026-10-31: picked without removing `className`",
            "Folded 2026-11-02: github_utils.py into the pick of bec637b61c.",
            "Plan 2026-10-29: x; Partial 2026-11-01: picked without y",
        ):
            self.assertEqual(m.carry_over("?", note, ""), ("?", note))
            self.assertEqual(m.carry_over("?", note, "?"), ("?", note))
            self.assertEqual(m.carry_over("?", note, "O"), ("O", note))

    def test_partial_note_only_keeps_a_question_mark(self):
        note = "Partial 2026-10-31: picked without x"
        self.assertEqual(m.carry_over("", note, ""), ("", note))
        self.assertEqual(m.carry_over("", note, "?"), ("?", note))
        self.assertEqual(m.carry_over("O", note, ""), ("O", note))

    def test_partial_mark_needs_its_date(self):
        for note in ("a partial pick", "Partial: no date", "Folded into 5b791b1641", "partial 2026-10-31: lowercase"):
            self.assertEqual(m.carry_over("?", note, ""), ("", note))

    def test_script_omission_released_then_rechecked(self):
        self.assertEqual(m.carry_over("?", "Cancels out (script): x [was: empty]", ""), ("", ""))
        self.assertEqual(m.carry_over("X", "Omitted (script): x [was: empty]", "?"), ("?", ""))


class ExcludeOnlyPathsTest(unittest.TestCase):
    PREFIXES = ["src", "lib-multisrc"]

    def test_only_under(self):
        self.assertTrue(m.only_under(["src/en/a/A.kt", "lib-multisrc/t/T.kt"], self.PREFIXES))
        self.assertFalse(m.only_under(["src/en/a/A.kt", "lib/x/X.kt"], self.PREFIXES))
        self.assertFalse(m.only_under(["srcfoo/x", "lib-multisrcx/y"], self.PREFIXES))
        self.assertFalse(m.only_under([], self.PREFIXES))  # empty commit is kept
        self.assertTrue(m.only_under(["README.md"], ["README.md"]))

    def test_parse_name_only(self):
        text = "\x1eaaa\n\nsrc/a\nlib/b\n\x1ebbb\n\x1eccc\n\nsrc/c\n"
        self.assertEqual(m.parse_name_only(text), {"aaa": ["src/a", "lib/b"], "bbb": [], "ccc": ["src/c"]})

    def test_module_of(self):
        self.assertEqual(m.module_of("src/en/foo/src/eu/kanade/Foo.kt"), "src/en/foo")
        self.assertEqual(m.module_of("src/en/foo/res/mipmap-hdpi/ic_launcher.png"), "src/en/foo")
        self.assertEqual(m.module_of("src/en/foo/assets/i18n/messages.properties"), "src/en/foo")
        self.assertEqual(m.module_of("src/en/foo/build.gradle"), "src/en/foo")
        self.assertEqual(m.module_of("lib-multisrc/madara/src/eu/kanade/Madara.kt"), "lib-multisrc/madara")
        self.assertEqual(m.module_of("lib-multisrc/madara/build.gradle.kts"), "lib-multisrc/madara")

    def test_config_is_normalized_and_checked(self):
        up = m.Upstream("u", "u", "main", "o/r", ["/src/", "lib-multisrc/", "/"])
        self.assertEqual(up.exclude_only_paths, ["src", "lib-multisrc"])
        self.assertEqual(m.Upstream("u", "u", "main", "o/r").exclude_only_paths, [])
        self.assertEqual(m.Upstream("u", "u", "main", "o/r").repo_wide_min_modules, 0)
        for bad in ({"exclude_only_paths": "src/"}, {"repo_wide_min_modules": "5"}, {"repo_wide_min_modules": -1}):
            with self.assertRaises(SystemExit):
                m.Upstream("u", "u", "main", "o/r", **bad)

    def test_build_drops_path_only_commits(self):
        prim = m.Upstream("prim", "prim", "main", "o/prim", ["src"])
        sec = m.Upstream("sec", "sec", "main", "o/sec", ["src"])
        cfg = m.Config("master", "2026-01-01", "out.md", "", prim, [sec])
        p_src = mk("1", "Fix a source", "2026-01-02", "prim")
        p_lib = mk("2", "Update a lib", "2026-01-03", "prim")
        s_pick = mk("3", f"Fix a source\n\n(cherry picked from commit {p_src.full})", "2026-01-04", "sec")
        s_src = mk("4", "Own source fix", "2026-01-05", "sec")
        changed = {
            p_src.full: ["src/en/a/A.kt"],
            p_lib.full: ["lib/x/X.kt", "src/en/a/A.kt"],
            s_pick.full: ["core/Util.kt"],
            s_src.full: ["src/all/b/B.kt"],
        }
        logs = {"prim": [p_lib, p_src], "sec": [s_src, s_pick]}

        entries = fake_builder(cfg, logs, changed).build()
        self.assertEqual([e.commit.full for e in entries], [p_lib.full, s_pick.full])
        pick = entries[1]
        self.assertEqual(pick.kind, "own")
        self.assertTrue(pick.pre_pick)
        self.assertIn("(not listed: only changes under src)", pick.details[0])

    def test_build_keeps_repo_wide_commits(self):
        prim = m.Upstream("prim", "prim", "main", "o/prim", ["src", "lib-multisrc"], repo_wide_min_modules=3)
        sec = m.Upstream("sec", "sec", "main", "o/sec")
        cfg = m.Config("master", "2026-01-01", "out.md", "", prim, [sec])
        narrow = mk("1", "Fix two sources", "2026-01-02", "prim")
        wide = mk("2", "Refactor UrlActivities", "2026-01-03", "prim")
        changed = {
            narrow.full: ["src/en/a/src/A.kt", "src/en/a/res/icon.png", "src/en/b/build.gradle"],
            wide.full: ["src/en/a/src/A.kt", "src/en/b/src/B.kt", "lib-multisrc/t/src/T.kt"],
        }
        entries = fake_builder(cfg, {"prim": [wide, narrow], "sec": []}, changed).build()
        self.assertEqual([e.commit.full for e in entries], [wide.full])
        self.assertEqual(entries[0].details[0], "repo-wide: 3 modules, only under src, lib-multisrc")

    def test_repo_wide_needs_a_threshold(self):
        prim = m.Upstream("prim", "prim", "main", "o/prim", ["src"])
        cfg = m.Config("master", "2026-01-01", "out.md", "", prim, [m.Upstream("sec", "sec", "main", "o/sec")])
        wide = mk("2", "Refactor everything", "2026-01-03", "prim")
        changed = {wide.full: [f"src/en/s{i}/src/S.kt" for i in range(50)]}
        self.assertEqual(fake_builder(cfg, {"prim": [wide], "sec": []}, changed).build(), [])


def fake_target_git(log, changes, files):
    """`git` for TargetIndex: the target branch's `log`, commits' (status, path) `changes`, and `rev:path` files."""

    def run(*args, cwd, check=True, stdin=None):
        if args[0] == "rev-list":
            return ""
        if args[0] == "cat-file":  # a rev exists when it has some file
            revs = [line.removesuffix("^{tree}") for line in stdin.split()]
            return "".join("tree\n" if any(f.startswith(f"{r}:") for f in files) else f"{r}^{{tree}} missing\n" for r in revs)
        if "ls-tree" in args:
            rev, roots = args[args.index("--") - 1], args[args.index("--") + 1 :]
            paths = [f.split(":", 1)[1] for f in files if f.startswith(f"{rev}:")]
            return "".join(f"{p}\0" for p in paths if any(p == r or p.startswith(f"{r}/") for r in roots))
        if "--name-status" in args:
            return "".join(f"\x1e{h}\n\n" + "".join(f"{st}\t{p}\n" for st, p in changes.get(h, [])) for h in stdin.split())
        return "".join(f"{c.full}\x1f{c.date}\x1fa\x1fa@x\x1fa\x1fa@x\x1f{c.message}\x1e" for c in log)

    return run


class PartialPickTest(unittest.TestCase):
    def test_parse_name_status(self):
        text = "\x1eaaa\n\nM\tcore/A.kt\nA\tlib/x/b c.kt\n\x1ebbb\n"
        self.assertEqual(m.parse_name_status(text), {"aaa": [("M", "core/A.kt"), ("A", "lib/x/b c.kt")], "bbb": []})

    def test_checked_paths(self):
        changes = [("M", "src/en/a/A.kt"), ("M", "core/U.kt"), ("A", "README.md")]
        self.assertEqual(m.checked_paths(m.Upstream("u", "u", "main", "o/r", ["src"]), changes), changes[1:])
        sec = m.Upstream("s", "s", "main", "o/s", partial_check_paths=["/src/"])
        self.assertEqual(sec.partial_check_paths, ["src"])
        self.assertEqual(m.checked_paths(sec, changes), changes[:1])
        with self.assertRaises(SystemExit):
            m.Upstream("s", "s", "main", "o/s", partial_check_paths="src/")

    def test_checked_paths_skips_ignored_paths(self):
        changes = [("M", "CHANGELOG.md"), ("M", "app/A.kt"), ("A", "data/migrations/9.sqm")]
        up = m.Upstream("u", "u", "main", "o/r", partial_ignore_paths=["CHANGELOG.md", "/data/migrations/"])
        self.assertEqual(up.partial_ignore_paths, ["CHANGELOG.md", "data/migrations"])
        self.assertEqual(m.checked_paths(up, changes), changes[1:2])
        with self.assertRaises(SystemExit):
            m.Upstream("u", "u", "main", "o/r", partial_ignore_paths="CHANGELOG.md")

    def test_unpicked_files(self):
        here = {"core/Old.kt", "core/Kept.kt", "lib/x/src/main/X.kt"}
        changes = [
            ("A", "core/New.kt"),  # picked
            ("M", "core/Kept.kt"),  # left out, the target has it
            ("A", "core/Skipped.kt"),  # left out, added to a module the target has
            ("D", "core/Old.kt"),  # left out, the target still has it
            ("D", "core/Gone.kt"),  # n/a: the target never had it
            ("M", "core/Dropped.kt"),  # n/a: changed upstream, but the target has no such file
            ("A", "lib/other/src/M.kt"),  # n/a: a lib the target doesn't have
            ("A", "lib/x/src/upstream/X.kt"),  # picked as lib/x/src/fork/X.kt
            ("M", "build.gradle.kts"),  # n/a: root file the target doesn't have
        ]
        picked = {"core/New.kt", "lib/x/src/fork/X.kt"}
        missing, applicable = m.unpicked_files(changes, picked, here)
        self.assertEqual(missing, ["core/Kept.kt", "core/Skipped.kt", "core/Old.kt"])
        self.assertEqual(applicable, 5)
        self.assertEqual(m.unpicked_files(changes[:1], picked, here), ([], 1))

    def test_unpicked_files_adapted_paths_and_new_modules(self):
        here = {"lib/x/src/fork/X.kt", "core/src/a/A.kt", "core/src/b/A.kt"}
        changes = [
            ("M", "lib/x/src/upstream/X.kt"),  # the target has it at an adapted path
            ("M", "core/src/a/A.kt"),  # the target has this path: a change to core/src/b/A.kt doesn't carry it
            ("A", "lib/new/src/One.kt"),  # picked
            ("A", "lib/new/src/Two.kt"),  # applies: the picks add the module
            ("M", "lib/y/src/upstream/Y.kt"),  # picked as lib/y/src/fork/Y.kt, which the target didn't have yet
        ]
        picked = {"core/src/b/A.kt", "lib/new/src/One.kt", "lib/y/src/fork/Y.kt"}
        missing = ["lib/x/src/upstream/X.kt", "core/src/a/A.kt", "lib/new/src/Two.kt"]
        self.assertEqual(m.unpicked_files(changes, picked, here), (missing, 5))
        self.assertEqual(m.unpicked_files(changes, picked | {"lib/x/src/fork/X.kt"}, here), (missing[1:], 5))

    def test_load_files_skips_a_root_commits_parent(self):
        files = {"c^:core/A.kt", "c^:lib/y/B.kt"}
        with mock.patch.object(m, "git", side_effect=fake_target_git([], {}, files)) as g:
            target = m.TargetIndex(Path("."), "master", [])
            target.load_files({"c^:core", "c^:lib/x", "root^:core"})
            target.load_files({"c^:core"})  # already read
        self.assertEqual(target.files["c^"], {"core/A.kt"})
        self.assertEqual(target.files["root^"], set())
        ls_tree = [call for call in g.call_args_list if "ls-tree" in call.args]
        self.assertEqual([call.args[call.args.index("--") - 1] for call in ls_tree], ["c^"])
        self.assertTrue(all(call.kwargs.get("check", True) for call in ls_tree))

    def test_partial_pick_is_maybe_until_the_rest_is_picked(self):
        prim = m.Upstream("prim", "prim", "main", "o/prim", ["src"])
        cfg = m.Config("master", "2026-01-01", "out.md", "", prim, [m.Upstream("sec", "sec", "main", "o/sec")])
        up = mk("1", "Add support for the new lib", "2026-01-02", "prim")
        part = mk("a", f"Add support for the new lib\n\n(cherry picked from commit {up.full})", "2026-01-03", "t")
        rest = mk("b", f"The rest of it\n\n(cherry picked from commit {up.full[:12]})", "2026-01-04", "t")
        changes = {
            up.full: [("A", "core/Cache.kt"), ("M", "core/Source.kt"), ("M", "src/en/a/A.kt")],
            part.full: [("A", "core/Cache.kt")],
            rest.full: [("M", "core/Source.kt")],
        }
        files = {f"{part.full}^:core/Source.kt"}

        def detect(log, done=()):
            builder = fake_builder(cfg, {"prim": [up], "sec": []})
            entries = builder.build()
            with mock.patch.object(m, "git", side_effect=fake_target_git(log, changes, files)):
                builder.detect(entries, done)
            return entries[0]

        e = detect([part])
        self.assertEqual(e.status, "?")  # the matching title doesn't make it O
        self.assertEqual(e.details, [f"master `{part.short}` [hash of prim (partial: 1 of 2 files not picked), title+author]"])
        e = detect([part], done={up.full})  # a row already marked O isn't checked
        self.assertEqual(e.status, "O")
        self.assertEqual(e.details, [f"master `{part.short}` [hash of prim, title+author]"])
        e = detect([rest, part])
        self.assertEqual(e.status, "O")
        # the hash names the newest pick, even though it cites a short hash
        self.assertEqual(e.details, [f"master `{rest.short}` [hash of prim]", f"master `{part.short}` [title+author]"])


class OrderTest(unittest.TestCase):
    def setUp(self):
        self.prim = m.Upstream("prim", "prim", "main", "o/prim")
        self.sec = m.Upstream("sec", "sec", "main", "o/sec", ["src"])
        self.cfg = m.Config("master", "2026-01-01", "out.md", "", self.prim, [self.sec])
        self.m1 = mk("1", "Older main commit", "2026-01-01", "prim")
        self.m2 = mk("2", "Newer main commit", "2026-01-03", "prim")

    def copy(self, ch, p, date):
        return mk(ch, f"{p.subject}\n\n(cherry picked from commit {p.full})", date, "sec")

    def test_copies_above_originals_and_own_commits_above_older_copies(self):
        ms1, ms2 = self.copy("3", self.m1, "2026-01-02"), self.copy("6", self.m2, "2026-01-06")
        s2, s3 = mk("4", "Own commit", "2026-01-04", "sec"), mk("5", "Another own commit", "2026-01-05", "sec")
        logs = {"prim": [self.m2, self.m1], "sec": [ms2, s3, s2, ms1]}

        entries = fake_builder(self.cfg, logs).build()
        self.assertEqual([e.commit for e in entries], [ms2, self.m2, s3, s2, ms1, self.m1])
        self.assertEqual([e.kind for e in entries], ["copy", "primary", "own", "own", "copy", "primary"])

    def test_own_commits_without_an_older_copy_go_last(self):
        s_old = mk("4", "Own commit", "2026-01-02", "sec")
        ms2 = self.copy("6", self.m2, "2026-01-06")
        logs = {"prim": [self.m2, self.m1], "sec": [ms2, s_old]}
        entries = fake_builder(self.cfg, logs).build()
        self.assertEqual([e.commit for e in entries], [ms2, self.m2, self.m1, s_old])

    def test_path_excluded_copy_does_not_anchor(self):
        ms1 = self.copy("3", self.m1, "2026-01-02")
        s2 = mk("4", "Own commit", "2026-01-04", "sec")
        logs = {"prim": [self.m2, self.m1], "sec": [s2, ms1]}
        entries = fake_builder(self.cfg, logs, {ms1.full: ["src/a/A.kt"]}).build()
        self.assertEqual([e.commit for e in entries], [self.m2, self.m1, s2])


if __name__ == "__main__":
    unittest.main()
