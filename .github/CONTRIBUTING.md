# Global Guidelines for Contributing


litebito/airsonic-pulse development is a community project, and contributions are welcomed. Here are a few guidelines you should follow before submitting:

  1.  **License Acceptance** All contributions must be licensed as [GNU GPLv3](https://github.com/litebito/airsonic-pulse/blob/main/LICENSE.txt) to be accepted. Use [`git commit --signoff`](https://jk.gs/git-commit.html) to acknowledge this.
  2.  **No Breakage** New features or changes to existing ones must not degrade the user experience. This means do not introduce bugs, remove functionality, or make large changes to existing themes/UI without prior discussion in an Issue.
  3.  **Coding standards** Language best-practices should be followed, comment generously, and avoid "clever" algorithms. Refactoring existing messes is great, but watch out for breakage.
  4.  **Be bold!** Without contributions, this project will vanish. If you just want to help out, try [submitting a patch](https://github.com/litebito/airsonic-pulse/issues?q=is%3Aissue+is%3Aopen+label%3Apatches-welcome) for an open Issue.
  5.  **Stay relevant** Issues or commentary that is off-topic or tangential to litebito/airsonic-pulse development is subject to moderation. Questions should be focused on improving documentation to solve a problem. Visit [GitHub Discussions](https://github.com/litebito/airsonic-pulse/discussions)
  

# Getting a .war

Airsonic is using [Maven](https://maven.apache.org/) to manage its build
process. Any version above 3.3+ should do the job.

Recent versions of Mavens block http repos. One dependency we use is on an http repo (clingline). As such we specify our own Maven settings file to allow Maven to fetch artifacts from the http repo.

If you want to run the testsuite and get a .war if everything goes fine, use this command:

```
 mvn clean package
```

If you don't care about the result of the testsuite, but only
want a `.war` as quick as possible, you can use this instead:

```
mvn -DskipTests -Dcheckstyle.skip=true clean package
```

# Suggesting modifications

Airsonic-Pulse's source code is hosted on [github](https://github.com/litebito/airsonic-pulse/), who provides a [lot of documentation](https://help.github.com/en) on how to contribute to projects hosted there.

Keep in mind that this is a non-funded community-driven project maintained by a relatively small group of contributors (today in 2026, just myself) who have many other responsibilities and demands on their time. 
Development, maintenance, and administration of the project is done on a best-effort basis, as time and other constraints permit.

# Getting your pull-requests reviewed and merged (the future option)

Once the sole maintainer has reached a certain goal with this project, the sole maintainer will be open for contributors and pull-requests, so below already is the guideline for such.

Once you have submitted a pull-request, a number of factors may determine how
much attention it receives, how quickly it may be reviewed, and whether or not
it eventually gets accepted and merged. Here are a few guidelines that can help
speed the process and increase the chances of a pull request being accepted and
merged in a timely fashion:

- Limit the scope to the minimum changes necessary to accomplish a discrete and
	well defined task.
- If your changes could be broken down into smaller units, then they are much
	less likely to be accepted. 
- Keep it small, efficient and light
- Try not to address unrelated issues in the same set of changes, unless
	failing to do so would cause other problems (like merge conflicts).
- Do your best to maintain prior functionality while eliminating (or at least
	minimizing) side effects.
- Changes that affect backward compatibility or make it more difficult to
	upgrade or downgrade versions of libraries or installations will be the most
	heavily scrutinized and take the longest.
- Maintain the style and coding standards represented by the codebase.
- Consistent, simple, and easy-to-understand changes are usually preferred.
- Do not mix functional changes with code cleanups or style changes.
- Make it as easy as possible for others to review your changes. Rebasing your
	PR to address issues is strongly preferred over adding additional commits
	that make changes to (or undo parts of) prior commits.
- In general, the more commits in a PR, the harder it is to review and the
	longer it will take to be reviewed. But do not sacrifice good change
	isolation by combining commits unnecessarily.
- If your PR needs more than 2 or 3 commits, then you *probably* need to reduce
	the scope of your PR. If a single commit touches more than a few files or
	more than 30-50 lines, then the scope of the commit *probably* needs to be
	reduced.
- Keep in mind that we strive to balance stability with new features while best
	utilizing everybody's limited available free time. As such, a pull request may
	be rejected if it doesn't strike that balance.

And finally:

- Actively maintain your PR. If any concerns are raised, or tests fail, or some
	other change occurs in the codebase that affects your PR (like a merge
	conflict) before your PR is accepted and merged, you are expected to address
	and resolve those issues or the PR may be rejected.

Once all concerns have been addressed, having a change accepted usually
requires two (or more, depending on complexity and impact) [core
contributors](https://github.com/litebito/airsonic-pulse/graphs/contributors): one to
explicitly approve the pull-request, and another to perform the actual merge.

If you keep sending great code, you might be invited to become a *core
contributor*.

Normal releases do not happen on any fixed schedule. They happen when the
maintainers collectively decide that enough changes and testing have taken
place that a new release is warranted. Bugfix releases (when a problem has been
discovered that is likely to impact users significantly), may happen more
quickly if needed. Even after acceptance, the inclusion of larger changes may
be delayed until a major version release in order to ensure that the impact to
users is minimized and that stability is maintained.