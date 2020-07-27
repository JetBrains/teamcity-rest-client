# Changelog

All notable changes to teamcity-rest-client library will be documented in this file.

## [Unreleased]

### Added 

- Option to expand multiple invocations of a test (`TestRunsLocator::expandMultipleInvocations`)

## [1.11.0] - 2020-07-15

### Added

- Changes fetched from build have vcsRootInstance (`Change::vcsRootInstance`)
- `Build::personal` property

## [1.10.0] - 2020-07-01

### Added

- Method to set comment for a build (`Build::setComment`)
- Additional methods to download artifacts in `BuildArtifact`
- Additional properties in `TestOccurence` to check whether a test failure is new or not (`newFailure`), to get the build it is first failed (`firstFailedIn`) and fixed (`fixedIn`) 
- Token-based authentication (`TeamCityInstanceFactory.tokenAuth`)
