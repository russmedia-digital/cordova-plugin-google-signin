const fs = require("fs");
const path = require("path");

function addProjectLevelDependency(platformRoot) {
    const artifactVersion = "com.google.gms:google-services:4.3.14";
    const dependency = 'classpath "' + artifactVersion + '"';

    const projectBuildFile = path.join(platformRoot, "build.gradle");

    let fileContents = fs.readFileSync(projectBuildFile, "utf8");

    const myRegexp = /\bclasspath\b.*/g;
    let match = myRegexp.exec(fileContents);
    if (match != null) {
        let insertLocation = match.index + match[0].length;

        fileContents =
            fileContents.substr(0, insertLocation) +
            "; " +
            dependency +
            fileContents.substr(insertLocation);

        fs.writeFileSync(projectBuildFile, fileContents, "utf8");

        console.log("updated " + projectBuildFile + " to include dependency " + dependency);
    } else {
        console.error("unable to insert dependency " + dependency);
    }
}

module.exports = context => {
    "use strict";
    const platformRoot = path.join(context.opts.projectRoot, "platforms/android");

    return new Promise((resolve, reject) => {
        addProjectLevelDependency(platformRoot);
        resolve();
    });
};
