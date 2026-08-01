# WilhelmOS Certification Model (ED-109A)

How WilhelmOS participates in a client's ED-109A approval — what a
client receives, what remains their work, and where the boundary runs.
Companion to [DESIGN.md](DESIGN.md) (§7 composition, §8 evidence
roadmap) and [KIOSK-CONTRACT.md](KIOSK-CONTRACT.md) (the technical
platform/application interface).

## Positioning: COTS, not certified product

WilhelmOS is **COTS software under ED-109A §12.4**. That framing is
load-bearing: under ED-109A, *nothing is certified in isolation*.
Approval attaches to the applicant's equipment in its operational
context — there is no such thing as a pre-certified operating system,
only pre-assembled evidence. WilhelmOS therefore never hands a client a
certificate; it hands them an **evidence package** purpose-built to slot
into the client's own assurance case.

The one-line version:

> **A client pins a specific WilhelmOS release and receives its complete
> evidence package, ready to reference in their own assurance case —
> instead of producing platform lifecycle evidence themselves. They
> inherit the evidence; they own the argument.**

## What a client receives per release

Each WilhelmOS release is a tagged, immutable configuration item —
tag → commit SHA → reproducible build → image hash, with every input
pinned (upstream Yocto layers by SHA, package sources by checksum). A
client consuming release `vX.Y.Z` receives:

1. **A defined COTS configuration item.** The pin in the client's own
   build configuration (see KIOSK-CONTRACT.md for the kas mechanism) is
   their CM record: "this equipment consumed WilhelmOS vX.Y.Z", and
   that name resolves to exact bits, forever. This satisfies the
   configuration-identification objectives outright.
2. **The evidence pack for that exact version:** SBOM (SPDX, per image,
   covering every package and library), configuration management
   records, test results, and the reproducibility demonstration —
   artifacts mapped to the §12.4.10 objectives.
3. **The COTS Software Integrity Assurance Case template (§12.4.11):**
   the skeleton argument for why this COTS platform is acceptable at
   the client's assurance level, which the client adapts and adopts.

The client does not have to *generate* any platform evidence. That is
the burden reduction WilhelmOS sells.

## What remains the applicant's work

The client (the ED-109A applicant) cannot "inherit certification" —
four things are structurally theirs:

1. **Assurance level assignment.** ALs come from the applicant's own
   safety assessment of their system's functions. COTS software has no
   inherent AL; the WilhelmOS evidence must be *argued sufficient* for
   whatever AL the client's system demands (the platform targets AL3–5
   viability).
2. **The applicability argument.** The client's PSAA (§11.1) must take
   the WilhelmOS evidence and argue: *this* evidence, for *this*
   version, suffices for *my* AL, configuration, and usage domain —
   including service-experience claims (§12.3.4) and mitigations for
   any gaps. The §12.4.11 template provides the skeleton; filling it in
   is applicant work a regulator will probe.
3. **The integration.** The client's image build — their kas
   composition, application selection, production credentials,
   deployment configuration — is a configuration item in *their* CM
   system. The layer boundary is the certification boundary
   (DESIGN.md §7): WilhelmOS never contains client software, and the
   client's composition acts are theirs to control and evidence.
4. **Their application.** Full ED-109A lifecycle (Sections 4–8) for the
   application software — requirements, design, verification,
   traceability — is untouched by the platform evidence. The boundary
   means neither side's evidence must speak about the other.

The commercial precedent is an RTOS certification kit (DO-178C
practice): the vendor sells an evidence package per version; the
applicant still owns the approval and the argument. What the client
buys is not certification but *not having to create the platform
evidence themselves* — from a vendor whose platform is minimal enough
that the argument stays short.

## Division of responsibility

| Concern | WilhelmOS (COTS vendor) | Client (applicant) |
|---|---|---|
| Platform lifecycle evidence (§12.4.10) | Produces, per release | References |
| Assurance case for the platform (§12.4.11) | Template + evidence | Adapts, argues applicability |
| AL assignment | — | From their safety assessment |
| PSAA (§11.1) | — | Produces |
| Image composition & deployment config | Mechanism (contract, kas) | Configuration item in their CM |
| Application software (Sections 4–8) | — | Full lifecycle |
| Safety monitoring mechanism (§2.4.3) | systemd supervision, watchdog | Policy for their services |
| Approval | — | Theirs, with their regulator |

## Current status

The *mechanism* is implemented and validated: versioned releases
(tag + SHA, kas-verified), per-image SBOM, reproducible pinned builds,
the composition contract with build-time enforcement, and CM discipline
end to end.

The *curated evidence pack* — test results formally mapped to §12.4.10
objectives and the filled-out §12.4.11 template — is Phase 3 of the
roadmap (DESIGN.md §8, TODO.md). Until it ships, a client gets the
baseline and the raw artifacts (SBOM, build reproducibility, CM
records); the "reference this pack in your PSAA" deliverable is the
remaining platform work.
