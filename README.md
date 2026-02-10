# Nimbus

Nimbus is the demo I use to learn Spring AI.

## Modules

### nimbus-mvp
- A minimal weather assistant wrapper.
- Supports weather query and clothing advice.
- Uses LLM for natural language response generation.

### nimbus-agent
- An upgraded version using Spring AI native tool-calling.
- Supports multi-turn conversation and session memory.
- Supports normal chat and stream chat endpoints.
- Includes basic tool trace for debugging and observation.

## Next

I will continue to deepen the `nimbus-agent` implementation, mainly around:
- concurrency stability,
- distributed session/memory,
- and better observability.

