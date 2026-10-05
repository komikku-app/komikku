"""Unit tests for update_cherrypick_log.py (stdlib only, no git repository needed).

Run: python3 -m unittest discover -s .claude/skills/cherrypick-log -p 'test_*.py'
"""

import sys
import unittest
from pathlib import Path

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

    def test_script_omission_released_then_rechecked(self):
        self.assertEqual(m.carry_over("?", "Cancels out (script): x [was: empty]", ""), ("", ""))
        self.assertEqual(m.carry_over("X", "Omitted (script): x [was: empty]", "?"), ("?", ""))


if __name__ == "__main__":
    unittest.main()
