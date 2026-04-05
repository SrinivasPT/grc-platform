# Goal
Build the GRC Platform detailed out in the `docs` folder. We need to build this step by step in phases so that we will not be overwhelemed

# Key Points
- VS Code GitHub Copilot Claude Sonnet 4.6 model to be used fully
    - Copilot Instructions copilot-instructions.md
    - Path specific instructions
    - Coding patterns for react, Java, graph, and DB so that code consistency is achieved

- Living documentation adoption
    - We need to use the design available at the `docs` folder as the living documentation. If this needs proper structuring, please go ahead and do that. 
    - Also we need to use the ADR (Architecture Design Decision)
    - Need to ensure that design, adr and the code are always in synch. Come up with the procedure to achieve this

- How to build this platform in managable phases
    - Setup the VS code instruction files - global and path specific instruction files
    - Basic Infrastrcture Setup - SQL Server, Neo4J, Redis, Password Valut, Tekton etc. all setup. 
    - Create the skeleton projects for React and Java projects for various modules

- Then completely build building block by building block. 

- Ensure that follow the Test Driven Approach. Ensure that code passes the tests. All these tests will become Regression and need to be maintained and tested everytime code is changed. Come up with a strategy and procedure for that and same need to be documented in copilot instructions.

**Note**: Its important that GitHub instrction files captures the essence of the development process like TDD, Patterns and practices, adherence to enterprise standrads like sonar, checkmaks, code quality etc is maintained and integrated into CI/CD and Tekton. Codeing agents trigger, and monitor issues and correct them or if there are any different way to achive and conformance suggest the document that as instructions

# Rough Notes
- We need to build the grc-platform detailed out in the `docs` folder. 
- Come up with a detailed plan with the step-step tasks so that we can build this whole platform. 
- This is a gigantic exercise and GitHub copilot agents are going to take the heavy lifter
- Setting up git hub copilot instructions global, folder level instructions for various layers like frontend, backend, and infrastructure
- Different technologies being used like React, Java and DB.
- We need to setup the infrastructure as well so that DB, Graph, Liquibase, Tekton CI/CD, etc is setup.
- May be starting with skeleton, all the required packages libraries isntalled, Java and rect project skeletons setup
- **Fully utilize the VS Code plugin** so that most of the work is done from VS Code itself.
- Password valut to be used for secret management with Tekton