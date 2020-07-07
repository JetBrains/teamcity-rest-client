# Changelog

All notable changes to teamcity-rest-client library will be documented in this file.

## [Unreleased]

- Changes fetched from build have vcsRootInstance

## [1.10.0] - 2020-07-01

### Added

- Method to set comment for a build (`Build::setComment`)
- Additional methods to download artifacts in `BuildArtifact`
- Additional properties in `TestOccurence` to check whether a test failure is new or not (`newFailure`), to get the build it is first failed (`firstFailedIn`) and fixed (`fixedIn`) 
- Token-based authentication (`TeamCityInstanceFactory.tokenAuth`)
