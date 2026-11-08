"""小乐 for the parents' phone. Run from this directory: `co host --background`. See ../RUNBOOK.md."""
import sys
import time
from pathlib import Path

from connectonion import Agent, before_llm, host
from connectonion.network.trust import TrustAgent
from connectonion.network.trust.trust_agent import Decision
from connectonion.network.trust.ws_admin import get_onboard_requirements

from ticket import edit_tickets, read_tickets, ticket_available

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "skills/xiaole"))


class FamilyTrust(TrustAgent):
    """Keep legacy invites, and admit each family ticket once."""

    @property
    def config(self):
        # Keep the door offered for refused tickets too, so ONBOARD_SUBMIT
        # can report Invalid invite code. Do not put tickets in fast_rules:
        # verify_invite may consume one, never a code attached to CONNECT.
        config = super().config
        onboard = dict(config.get("onboard", {}))
        codes = onboard.get("invite_code", [])
        if isinstance(codes, str):
            codes = [codes]
        onboard["invite_code"] = codes + list(read_tickets(self._co_dir))
        return {**config, "onboard": onboard}

    def should_allow(self, client_id, request=None):
        if self.get_level(client_id) == "stranger" and get_onboard_requirements(self):
            return Decision(allow=False, reason="Onboard required")
        return super().should_allow(client_id, request)

    def verify_invite(self, client_id, invite_code):
        with edit_tickets(self._co_dir) as tickets:
            record = tickets.get(invite_code)
            if record is None:
                return super().verify_invite(client_id, invite_code)
            now = time.time()
            if not ticket_available(record, now):
                return False
            self.promote_to_contact(client_id)
            record.update(address=client_id, used_at=now)
            print(f"✓ {record['label']} joined: {client_id[:16]}...")
            return True


@before_llm
def refresh_prompt(agent):
    # Stored sessions may carry instructions from before the host restarted.
    agent.current_session["messages"][0] = {"role": "system", "content": agent.system_prompt}


def main():
    from child import tell_child
    from rem import recent_sessions, search_notebook, show_page
    from voice import transcribe_voice

    skill = (HERE.parent / "skills/xiaole/SKILL.md").read_text().split("---", 2)[2]
    agent = Agent("小乐", system_prompt=skill, tools=[transcribe_voice, recent_sessions, show_page, search_notebook, tell_child],
                  on_events=[refresh_prompt])
    host(agent, trust=FamilyTrust(co_dir=HERE / ".co"), co_dir=HERE / ".co")


if __name__ == "__main__":
    main()
